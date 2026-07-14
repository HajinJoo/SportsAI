package com.example.sportsai.data

import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.Finding
import com.example.sportsai.model.FindingType
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.LandmarkPoint
import com.example.sportsai.model.Sport
import com.example.sportsai.model.TechniqueReport
import com.example.sportsai.model.metrics
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Rule-based biomechanics engine. Turns the raw pose timeline from [PoseAnalyzer]
 * into human-readable coaching feedback for a baseball pitch.
 *
 * It is deliberately simple and explainable: it measures joint angles per frame,
 * finds the key moments of the delivery, and compares them against good-form
 * reference ranges.
 */
class TechniqueAnalyzer {

    // ML Kit PoseLandmark type constants.
    private companion object {
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_ELBOW = 13
        const val RIGHT_ELBOW = 14
        const val LEFT_WRIST = 15
        const val RIGHT_WRIST = 16
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_KNEE = 25
        const val RIGHT_KNEE = 26
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28
        const val MIN_LIKELIHOOD = 0.5f
    }

    fun analyze(result: AnalysisResult, sport: Sport = Sport.BASEBALL_PITCH): TechniqueReport {
        if (result.timeline.size < 3) {
            return notEnoughData(result, sport)
        }
        return when (sport) {
            Sport.BASEBALL_PITCH -> analyzeBaseball(result)
            Sport.BASEBALL_BAT -> analyzeBatting(result)
            Sport.BASKETBALL_SHOT -> analyzeBasketball(result)
        }
    }

    private fun notEnoughData(result: AnalysisResult, sport: Sport): TechniqueReport =
        TechniqueReport(
            sport = sport.displayName,
            overallScore = 0,
            summary = "Not enough of your body was visible to analyze. " +
                "Film from the side, full body in frame, in good lighting.",
            findings = listOf(
                Finding(FindingType.TIP, "Filming", sport.filmingTip)
            ),
            detectionRate = result.detectionRate,
            metricScores = sport.metrics.associate { it.name to 0 },
            aiOverview = "The clip did not contain enough consistently visible body positions for a reliable skill rating. " +
                "That means the current scores should be treated as an incomplete baseline. " +
                "Record the full movement from the recommended angle with brighter, even lighting. " +
                "A clearer clip will let SportsAI identify your strongest skill and the next area to improve."
        )

    private fun analyzeBaseball(result: AnalysisResult): TechniqueReport {
        val findings = mutableListOf<Finding>()
        val sport = Sport.BASEBALL_PITCH.displayName

        // 1. Peak elbow flexion of the throwing arm (arm should stay flexed, not rigid).
        val peakElbowFlex = result.timeline.maxOfOrNull { frame ->
            val angle = throwingElbowAngle(frame)
            if (angle == null) 0.0 else 180.0 - angle // flexion = deviation from straight
        } ?: 0.0

        // 2. Stride knee flexion at landing (front leg should brace, not collapse or stay stiff).
        val strideKneeFlexRange = result.timeline.mapNotNull { strideKneeFlexion(it) }
        val maxStrideKneeFlex = strideKneeFlexRange.maxOrNull() ?: 0.0

        // 3. Trunk lean range — how much the torso rotates/tilts through the delivery.
        val trunkTilts = result.timeline.mapNotNull { trunkTilt(it) }
        val trunkRange = if (trunkTilts.size >= 2) trunkTilts.max() - trunkTilts.min() else 0.0

        // 4. Balance / head stability — vertical movement of the head across the clip.
        val headYs = result.timeline.mapNotNull { headY(it) }
        val headDrift = if (headYs.size >= 2) headYs.max() - headYs.min() else 0f

        var score = 60

        // --- Elbow ---
        when {
            peakElbowFlex in 70.0..110.0 -> {
                findings += Finding(
                    FindingType.GOOD, "Arm action",
                    "Good elbow flexion at cocking (~${peakElbowFlex.toInt()}°) — your arm stays " +
                        "loaded instead of stiff, which protects the elbow and adds whip."
                )
                score += 12
            }
            peakElbowFlex < 70.0 -> {
                findings += Finding(
                    FindingType.ISSUE, "Arm action",
                    "Your throwing elbow stays fairly straight (only ~${peakElbowFlex.toInt()}° of " +
                        "flex). A straighter arm can rob velocity and stress the elbow."
                )
                findings += Finding(
                    FindingType.TIP, "Arm action",
                    "Let the forearm lay back into an 'L' shape at the top of your arm swing."
                )
            }
            else -> {
                findings += Finding(
                    FindingType.TIP, "Arm action",
                    "Very deep elbow bend detected — make sure the arm gets up to shoulder height " +
                        "on time so it isn't late."
                )
                score += 4
            }
        }

        // --- Stride leg ---
        when {
            maxStrideKneeFlex in 20.0..55.0 -> {
                findings += Finding(
                    FindingType.GOOD, "Lower body",
                    "Your front leg braces well at landing — that firm base lets your energy " +
                        "transfer up into the throw."
                )
                score += 12
            }
            maxStrideKneeFlex > 55.0 -> {
                findings += Finding(
                    FindingType.ISSUE, "Lower body",
                    "Your front knee keeps bending through release (~${maxStrideKneeFlex.toInt()}°), " +
                        "so you're 'sitting' into the pitch and leaking power."
                )
                findings += Finding(
                    FindingType.TIP, "Lower body",
                    "Stride a touch shorter and think about landing on a firm front leg to hit against."
                )
            }
            else -> {
                findings += Finding(
                    FindingType.ISSUE, "Lower body",
                    "Your front leg stays very stiff. A little knee bend at landing helps absorb and " +
                        "redirect force."
                )
            }
        }

        // --- Trunk ---
        if (trunkRange >= 15.0) {
            findings += Finding(
                FindingType.GOOD, "Trunk",
                "Good torso rotation through the delivery — you're using your core, not just your arm."
            )
            score += 8
        } else {
            findings += Finding(
                FindingType.ISSUE, "Trunk",
                "Limited trunk rotation detected. Much of the throw looks arm-driven, which caps " +
                    "velocity and adds strain."
            )
            findings += Finding(
                FindingType.TIP, "Trunk",
                "Delay your chest rotation until the front foot lands, then rotate hips-then-shoulders."
            )
        }

        // --- Balance ---
        if (headDrift <= 0.18f) {
            findings += Finding(
                FindingType.GOOD, "Balance",
                "Your head stays quiet and level — a stable head means better control and repeatability."
            )
            score += 8
        } else {
            findings += Finding(
                FindingType.ISSUE, "Balance",
                "Your head moves a lot during the delivery, which hurts consistency and command."
            )
            findings += Finding(
                FindingType.TIP, "Balance",
                "Try to keep your eyes level and drive toward the target in a straight line."
            )
        }

        if (result.detectionRate < 0.6f) {
            findings += Finding(
                FindingType.TIP, "Filming",
                "Only part of your body was consistently visible. Better lighting and a side-on, " +
                    "full-body angle will make the analysis more accurate."
            )
        }

        score = score.coerceIn(0, 100)
        val summary = buildSummary(score)

        val armActionScore = when {
            peakElbowFlex in 70.0..110.0 -> 88
            peakElbowFlex > 110.0 -> 62
            else -> 40
        }
        val lowerBodyScore = when {
            maxStrideKneeFlex in 20.0..55.0 -> 85
            maxStrideKneeFlex > 55.0 -> 42
            else -> 38
        }
        val trunkScore = if (trunkRange >= 15.0) 82 else 40
        val balanceScore = if (headDrift <= 0.18f) 87 else 38
        val speedPotentialScore = wristSpeedScore(result.timeline)

        val metricScores = mapOf(
            "Pitch Speed Potential" to speedPotentialScore,
            "Arm Action" to armActionScore,
            "Stride Power" to lowerBodyScore,
            "Trunk Rotation" to trunkScore,
            "Balance" to balanceScore
        )
        val overview = buildOverview(sport, score, metricScores)

        return TechniqueReport(sport, score, summary, findings, result.detectionRate, metricScores, overview)
    }

    private fun buildSummary(score: Int): String = when {
        score >= 80 -> "Strong mechanics overall. A few tweaks will push you further."
        score >= 60 -> "Solid foundation with some clear areas to clean up."
        else -> "Lots of room to grow — focus on the flagged issues one at a time."
    }

    private fun analyzeBatting(result: AnalysisResult): TechniqueReport {
        val findings = mutableListOf<Finding>()
        val sport = Sport.BASEBALL_BAT.displayName
        var score = 60

        // 1. Hip/trunk rotation — a good swing rotates the core through contact.
        val trunkTilts = result.timeline.mapNotNull { trunkTilt(it) }
        val trunkRange = if (trunkTilts.size >= 2) trunkTilts.max() - trunkTilts.min() else 0.0

        // 2. Head stability — keep your eyes on the ball; the head should stay quiet.
        val headYs = result.timeline.mapNotNull { headY(it) }
        val headDrift = if (headYs.size >= 2) headYs.max() - headYs.min() else 0f

        // 3. Lead-arm extension through contact (proxy: peak elbow extension).
        val armExtension = result.timeline.mapNotNull { throwingElbowAngle(it) }.maxOrNull() ?: 0.0

        // 4. Lower-body load — some knee bend to load and drive.
        val kneeFlexes = result.timeline.mapNotNull { shootingKneeFlexion(it) }
        val maxKneeFlex = kneeFlexes.maxOrNull() ?: 0.0

        // --- Rotation ---
        if (trunkRange >= 20.0) {
            findings += Finding(
                FindingType.GOOD, "Rotation",
                "Strong hip and trunk rotation through the swing — that's where bat speed and power come from."
            )
            score += 14
        } else {
            findings += Finding(
                FindingType.ISSUE, "Rotation",
                "Limited body rotation detected — the swing looks arm-driven, which saps power."
            )
            findings += Finding(
                FindingType.TIP, "Rotation",
                "Start the swing by rotating your hips, then let your shoulders and hands follow."
            )
        }

        // --- Head / eyes ---
        if (headDrift <= 0.18f) {
            findings += Finding(
                FindingType.GOOD, "Head & eyes",
                "Your head stays quiet — that keeps your eyes on the ball for better contact."
            )
            score += 10
        } else {
            findings += Finding(
                FindingType.ISSUE, "Head & eyes",
                "Your head moves a lot during the swing, which pulls your eyes off the ball."
            )
            findings += Finding(
                FindingType.TIP, "Head & eyes",
                "Keep your head down and still; try to 'see' the bat meet the ball."
            )
        }

        // --- Arm extension ---
        if (armExtension >= 150.0) {
            findings += Finding(
                FindingType.GOOD, "Extension",
                "Good arm extension through the zone — you're driving the barrel out to the ball."
            )
            score += 10
        } else {
            findings += Finding(
                FindingType.ISSUE, "Extension",
                "Your arms stay bent through contact (~${armExtension.toInt()}°), which shortens your reach and power."
            )
            findings += Finding(
                FindingType.TIP, "Extension",
                "Think about extending your hands through the ball toward the pitcher after contact."
            )
        }

        // --- Lower body load ---
        if (maxKneeFlex in 15.0..55.0) {
            findings += Finding(
                FindingType.GOOD, "Lower body",
                "Nice knee bend to load your legs — a good base lets you drive through the ball."
            )
            score += 8
        } else if (maxKneeFlex < 15.0) {
            findings += Finding(
                FindingType.ISSUE, "Lower body",
                "Very little knee bend — you're standing tall and swinging with your upper body only."
            )
            findings += Finding(
                FindingType.TIP, "Lower body",
                "Get into an athletic stance with soft knees so you can load and explode."
            )
        }

        if (result.detectionRate < 0.6f) {
            findings += Finding(
                FindingType.TIP, "Filming",
                "Only part of your body was consistently visible. A side-on, full-body angle with " +
                    "good lighting improves accuracy."
            )
        }

        score = score.coerceIn(0, 100)

        val rotationScore = if (trunkRange >= 20.0) 90 else 40
        val headEyesScore = if (headDrift <= 0.18f) 85 else 38
        val extensionScore = if (armExtension >= 150.0) 88 else 42
        val lowerBodyScore = when {
            maxKneeFlex in 15.0..55.0 -> 82
            maxKneeFlex < 15.0 -> 35
            else -> 55
        }
        val batSpeedPotential = wristSpeedScore(result.timeline)

        val metricScores = mapOf(
            "Bat Speed Potential" to batSpeedPotential,
            "Ball Tracking" to headEyesScore,
            "Hip Rotation" to rotationScore,
            "Contact Extension" to extensionScore,
            "Lower Body Power" to lowerBodyScore
        )
        val overview = buildOverview(sport, score, metricScores)

        return TechniqueReport(sport, score, buildSummary(score), findings, result.detectionRate, metricScores, overview)
    }

    private fun analyzeBasketball(result: AnalysisResult): TechniqueReport {
        val findings = mutableListOf<Finding>()
        val sport = Sport.BASKETBALL_SHOT.displayName
        var score = 60

        // 1. Shooting elbow: should bend to ~90° at the set point, then extend on release.
        val elbowAngles = result.timeline.mapNotNull { shootingElbowAngle(it) }
        val minElbow = elbowAngles.minOrNull() ?: 180.0   // deepest bend (set point)
        val maxElbow = elbowAngles.maxOrNull() ?: 0.0      // extension (follow-through)

        // 2. Knee bend for leg drive: need a dip then a rise.
        val kneeFlexes = result.timeline.mapNotNull { shootingKneeFlexion(it) }
        val maxKneeFlex = kneeFlexes.maxOrNull() ?: 0.0

        // 3. Release alignment: at the most-extended-elbow frame, wrist should be above
        //    elbow above shoulder (a vertical shooting pocket).
        val releaseFrame = result.timeline.maxByOrNull { shootingElbowAngle(it) ?: 0.0 }
        val alignedRelease = releaseFrame?.let { verticalRelease(it) } ?: false

        // 4. Balance: quiet head.
        val headYs = result.timeline.mapNotNull { headY(it) }
        val headDrift = if (headYs.size >= 2) headYs.max() - headYs.min() else 0f

        // --- Elbow set point ---
        val setBend = 180.0 - minElbow
        when {
            setBend in 70.0..110.0 -> {
                findings += Finding(
                    FindingType.GOOD, "Set point",
                    "Nice ~90° elbow bend in your shooting pocket — that's an efficient, repeatable base."
                )
                score += 12
            }
            setBend < 70.0 -> {
                findings += Finding(
                    FindingType.ISSUE, "Set point",
                    "Your shooting elbow doesn't bend much (~${setBend.toInt()}°). A flatter, pushed " +
                        "shot is harder to control and loses arc."
                )
                findings += Finding(
                    FindingType.TIP, "Set point",
                    "Bring the ball into a set point with your elbow bent near 90° under the ball."
                )
            }
            else -> {
                findings += Finding(
                    FindingType.TIP, "Set point",
                    "Very deep elbow bend — make sure the ball still gets up on time for a smooth release."
                )
                score += 4
            }
        }

        // --- Follow-through extension ---
        if (maxElbow >= 155.0) {
            findings += Finding(
                FindingType.GOOD, "Follow-through",
                "Full arm extension on release — good for a soft, high-arcing shot."
            )
            score += 10
        } else {
            findings += Finding(
                FindingType.ISSUE, "Follow-through",
                "Your arm doesn't fully extend on release (~${maxElbow.toInt()}°), which flattens " +
                    "the shot."
            )
            findings += Finding(
                FindingType.TIP, "Follow-through",
                "Finish with your arm up and wrist snapped down — hold the 'gooseneck'."
            )
        }

        // --- Leg drive ---
        if (maxKneeFlex in 25.0..70.0) {
            findings += Finding(
                FindingType.GOOD, "Leg drive",
                "Good knee bend to load your legs — power should start from the ground up."
            )
            score += 10
        } else if (maxKneeFlex < 25.0) {
            findings += Finding(
                FindingType.ISSUE, "Leg drive",
                "Little knee bend detected — you're shooting mostly with your arms, which limits " +
                    "range and consistency when tired."
            )
            findings += Finding(
                FindingType.TIP, "Leg drive",
                "Dip into your knees and drive up through the shot in one smooth motion."
            )
        } else {
            findings += Finding(
                FindingType.TIP, "Leg drive",
                "Very deep dip — keep it athletic but avoid over-crouching, which slows your release."
            )
            score += 4
        }

        // --- Release alignment ---
        if (alignedRelease) {
            findings += Finding(
                FindingType.GOOD, "Alignment",
                "Your wrist, elbow and shoulder stack up nicely at release — straight-line power to the rim."
            )
            score += 8
        } else {
            findings += Finding(
                FindingType.ISSUE, "Alignment",
                "Your shooting arm looks off-line at release (elbow flaring out), which sends shots left/right."
            )
            findings += Finding(
                FindingType.TIP, "Alignment",
                "Tuck the elbow under the ball so wrist-elbow-shoulder form a vertical line."
            )
        }

        // --- Balance ---
        if (headDrift <= 0.20f) {
            findings += Finding(
                FindingType.GOOD, "Balance",
                "Good balance — a quiet head and stable base make your shot repeatable."
            )
            score += 6
        } else {
            findings += Finding(
                FindingType.ISSUE, "Balance",
                "You drift during the shot. Fading or leaning hurts your consistency."
            )
            findings += Finding(
                FindingType.TIP, "Balance",
                "Land roughly where you jumped from and keep your eyes level on the rim."
            )
        }

        if (result.detectionRate < 0.6f) {
            findings += Finding(
                FindingType.TIP, "Filming",
                "Only part of your body was consistently visible. A side-on, full-body angle with " +
                    "good lighting improves accuracy."
            )
        }

        score = score.coerceIn(0, 100)

        val setPointScore = when {
            setBend in 70.0..110.0 -> 90
            setBend < 70.0 -> 38
            else -> 55
        }
        val followThroughScore = if (maxElbow >= 155.0) 88 else 40
        val legDriveScore = when {
            maxKneeFlex in 25.0..70.0 -> 85
            maxKneeFlex < 25.0 -> 35
            else -> 55
        }
        val alignmentScore = if (alignedRelease) 87 else 40
        val balanceScore = if (headDrift <= 0.20f) 85 else 38
        val releaseSpeedScore = wristSpeedScore(result.timeline)
        val ballTrackingScore = if (headDrift <= 0.20f) 86 else 40

        val metricScores = mapOf(
            "Release Speed" to releaseSpeedScore,
            "Ball Tracking" to ballTrackingScore,
            "Set Point" to setPointScore,
            "Follow-through" to followThroughScore,
            "Leg Drive" to legDriveScore,
            "Alignment" to alignmentScore,
            "Balance" to balanceScore
        )
        val overview = buildOverview(sport, score, metricScores)

        return TechniqueReport(sport, score, buildSummary(score), findings, result.detectionRate, metricScores, overview)
    }

    private fun buildOverview(sport: String, score: Int, metrics: Map<String, Int>): String {
        val best = metrics.maxByOrNull { it.value }
        val worst = metrics.minByOrNull { it.value }
        val rating = when {
            score >= 80 -> "excellent"
            score >= 60 -> "solid"
            else -> "developing"
        }
        return buildString {
            append("Your $sport technique scores $score/100 overall, showing $rating form. ")
            if (best != null) append("${best.key} is currently your strongest area at ${best.value}/100. ")
            if (worst != null && worst.key != best?.key) {
                append("Your clearest opportunity is ${worst.key}, which scored ${worst.value}/100. ")
                append("Use the recommended drill in your next session, then compare this metric again to confirm progress.")
            } else {
                append("Your measured areas are well balanced. ")
                append("Keep refining the same movement and compare another clip to confirm that consistency.")
            }
        }
    }

    /** Converts normalized wrist travel into a repeatable 0–100 speed-potential score. */
    private fun wristSpeedScore(timeline: List<FramePose>): Int {
        val speeds = timeline.zipWithNext().mapNotNull { (previous, current) ->
            val elapsedSeconds = (current.timestampMs - previous.timestampMs) / 1_000.0
            if (elapsedSeconds <= 0.0) return@mapNotNull null
            val leftShoulder = current.point(LEFT_SHOULDER)
            val rightShoulder = current.point(RIGHT_SHOULDER)
            if (leftShoulder == null || rightShoulder == null) return@mapNotNull null
            val bodyScale = hypot(
                (rightShoulder.x - leftShoulder.x).toDouble(),
                (rightShoulder.y - leftShoulder.y).toDouble()
            ).coerceAtLeast(1.0)

            val wristSpeeds = listOf(LEFT_WRIST, RIGHT_WRIST).mapNotNull { type ->
                val from = previous.point(type) ?: return@mapNotNull null
                val to = current.point(type) ?: return@mapNotNull null
                hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble()) /
                    bodyScale / elapsedSeconds
            }
            wristSpeeds.maxOrNull()
        }.sorted()
        if (speeds.isEmpty()) return 35
        val representative = speeds[(speeds.lastIndex * 0.8).roundToInt()]
        return (35.0 + representative * 11.0).roundToInt().coerceIn(35, 96)
    }

    // --- Geometry helpers -------------------------------------------------

    private fun FramePose.point(type: Int): LandmarkPoint? =
        landmarks.firstOrNull { it.type == type && it.inFrameLikelihood >= MIN_LIKELIHOOD }

    /** Angle ABC in degrees at vertex B. */
    private fun angle(a: LandmarkPoint, b: LandmarkPoint, c: LandmarkPoint): Double {
        val abx = a.x - b.x; val aby = a.y - b.y
        val cbx = c.x - b.x; val cby = c.y - b.y
        val dot = abx * cbx + aby * cby
        val magAb = hypot(abx.toDouble(), aby.toDouble())
        val magCb = hypot(cbx.toDouble(), cby.toDouble())
        if (magAb == 0.0 || magCb == 0.0) return 180.0
        val cos = (dot / (magAb * magCb)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(kotlin.math.acos(cos))
    }

    /** Throwing arm assumed to be the one whose wrist is higher (smaller y) on average. */
    private fun throwingSide(frame: FramePose): Boolean? {
        val lw = frame.point(LEFT_WRIST)
        val rw = frame.point(RIGHT_WRIST)
        return when {
            lw != null && rw != null -> rw.y < lw.y // true => right side is throwing
            rw != null -> true
            lw != null -> false
            else -> null
        }
    }

    private fun throwingElbowAngle(frame: FramePose): Double? {
        val right = throwingSide(frame) ?: return null
        val shoulder = frame.point(if (right) RIGHT_SHOULDER else LEFT_SHOULDER) ?: return null
        val elbow = frame.point(if (right) RIGHT_ELBOW else LEFT_ELBOW) ?: return null
        val wrist = frame.point(if (right) RIGHT_WRIST else LEFT_WRIST) ?: return null
        return angle(shoulder, elbow, wrist)
    }

    /** Stride (front) leg is approximated as the leg opposite the throwing arm. */
    private fun strideKneeFlexion(frame: FramePose): Double? {
        val right = throwingSide(frame) ?: return null
        val hip = frame.point(if (right) LEFT_HIP else RIGHT_HIP) ?: return null
        val knee = frame.point(if (right) LEFT_KNEE else RIGHT_KNEE) ?: return null
        val ankle = frame.point(if (right) LEFT_ANKLE else RIGHT_ANKLE) ?: return null
        return 180.0 - angle(hip, knee, ankle)
    }

    private fun trunkTilt(frame: FramePose): Double? {
        val ls = frame.point(LEFT_SHOULDER)
        val rs = frame.point(RIGHT_SHOULDER)
        val lh = frame.point(LEFT_HIP)
        val rh = frame.point(RIGHT_HIP)
        if (ls == null || rs == null || lh == null || rh == null) return null
        val shoulderMidX = (ls.x + rs.x) / 2f
        val shoulderMidY = (ls.y + rs.y) / 2f
        val hipMidX = (lh.x + rh.x) / 2f
        val hipMidY = (lh.y + rh.y) / 2f
        val angleRad = atan2((shoulderMidX - hipMidX).toDouble(), (hipMidY - shoulderMidY).toDouble())
        return Math.toDegrees(angleRad)
    }

    private fun headY(frame: FramePose): Float? {
        val ls = frame.point(LEFT_SHOULDER)
        val rs = frame.point(RIGHT_SHOULDER)
        // Use shoulder mid-height as a stable proxy; normalize by torso length.
        if (ls == null || rs == null) return null
        val lh = frame.point(LEFT_HIP)
        val rh = frame.point(RIGHT_HIP) ?: lh
        val shoulderY = (ls.y + rs.y) / 2f
        val torso = if (lh != null && rh != null) {
            val hipY = (lh.y + rh.y) / 2f
            max(abs(hipY - shoulderY), 1f)
        } else 1f
        return shoulderY / torso
    }

    // --- Basketball helpers ----------------------------------------------

    /** Shooting arm assumed to be the higher hand (same heuristic as throwing arm). */
    private fun shootingElbowAngle(frame: FramePose): Double? = throwingElbowAngle(frame)

    /** Deepest knee bend across both legs (leg drive load). */
    private fun shootingKneeFlexion(frame: FramePose): Double? {
        val left = kneeFlexion(frame, right = false)
        val right = kneeFlexion(frame, right = true)
        return listOfNotNull(left, right).maxOrNull()
    }

    private fun kneeFlexion(frame: FramePose, right: Boolean): Double? {
        val hip = frame.point(if (right) RIGHT_HIP else LEFT_HIP) ?: return null
        val knee = frame.point(if (right) RIGHT_KNEE else LEFT_KNEE) ?: return null
        val ankle = frame.point(if (right) RIGHT_ANKLE else LEFT_ANKLE) ?: return null
        return 180.0 - angle(hip, knee, ankle)
    }

    /** True if shooting wrist is above elbow above shoulder and roughly vertically stacked. */
    private fun verticalRelease(frame: FramePose): Boolean {
        val right = throwingSide(frame) ?: return false
        val shoulder = frame.point(if (right) RIGHT_SHOULDER else LEFT_SHOULDER) ?: return false
        val elbow = frame.point(if (right) RIGHT_ELBOW else LEFT_ELBOW) ?: return false
        val wrist = frame.point(if (right) RIGHT_WRIST else LEFT_WRIST) ?: return false
        // Smaller y = higher on screen.
        val stackedVertically = wrist.y < elbow.y && elbow.y < shoulder.y
        val torsoWidth = max(abs(shoulder.x - elbow.x), 1f)
        val horizontalSpread = abs(wrist.x - shoulder.x) + abs(elbow.x - shoulder.x)
        val aligned = horizontalSpread < torsoWidth * 2.0f
        return stackedVertically && aligned
    }
}
