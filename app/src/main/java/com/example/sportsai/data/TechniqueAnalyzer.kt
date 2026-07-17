package com.example.sportsai.data

import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.AnalysisProfiles
import com.example.sportsai.model.AthleteTrackingMode
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
        val report = if (sport == Sport.BASEBALL_BAT &&
            result.athleteTracking.mode != AthleteTrackingMode.BATTER_LOCKED
        ) {
            notEnoughData(result, sport)
        } else if (result.timeline.size < 3) {
            notEnoughData(result, sport)
        } else when (sport) {
            Sport.BASEBALL_PITCH -> analyzeBaseball(result)
            Sport.BASEBALL_BAT -> analyzeBatting(result)
            Sport.BASKETBALL_SHOT -> analyzeBasketball(result)
        }
        return report.copy(
            analysisProfile = AnalysisProfiles.offline(sport),
            swingAnalysis = if (sport == Sport.BASEBALL_BAT) {
                SwingMechanicsAnalyzer().analyze(result)
            } else {
                null
            }
        )
    }

    private fun notEnoughData(result: AnalysisResult, sport: Sport): TechniqueReport {
        if (sport == Sport.BASEBALL_BAT &&
            result.athleteTracking.mode != AthleteTrackingMode.BATTER_LOCKED
        ) {
            val people = result.athleteTracking.maxPeopleDetected
            val detail = if (people > 1) {
                "SportsAI found up to $people people, but no single track had enough two-hand swing evidence to identify the batter safely."
            } else {
                "SportsAI could not follow one visible batter through enough of the swing to score it safely."
            }
            return TechniqueReport(
                sport = sport.displayName,
                overallScore = 0,
                summary = "Batter Lock was uncertain, so SportsAI did not score the catcher or umpire by mistake.",
                findings = listOf(
                    Finding(FindingType.TIP, "Batter Lock", detail),
                    Finding(
                        FindingType.TIP,
                        "Filming",
                        "Keep the batter's full body and both hands visible, use a side angle, and trim the clip to one complete swing."
                    )
                ),
                detectionRate = result.detectionRate,
                metricScores = emptyMap(),
                aiOverview = "Batter Lock could not confidently separate one complete batter track, so no technique score was created. " +
                    "This prevents catcher or umpire movement from becoming your result. " +
                    "Record one complete swing with the batter's hands, hips, knees, and feet visible. " +
                    "A clearer clip will unlock the offline mechanics and progress measurements."
            )
        }
        return TechniqueReport(
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
    }

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
        val requiredSamples = maxOf(3, (result.timeline.size * 0.35).roundToInt())
        val missingAreas = mutableListOf<String>()
        val metricScores = linkedMapOf<String, Int>()
        val weightedScores = mutableListOf<Pair<Int, Int>>()

        if (result.athleteTracking.mode == AthleteTrackingMode.BATTER_LOCKED) {
            val trackQuality = (result.athleteTracking.matchScore * 100).roundToInt()
            val otherPeople = (result.athleteTracking.maxPeopleDetected - 1).coerceAtLeast(0)
            findings += Finding(
                FindingType.GOOD,
                "Batter Lock",
                if (otherPeople > 0) {
                    "Followed one continuous swing-like batter across ${result.timeline.size} frames " +
                        "and separated it from up to $otherPeople other people " +
                        "(track quality $trackQuality/100)."
                } else {
                    "Used the continuous person track with the strongest swing pattern across " +
                        "${result.timeline.size} frames (track quality $trackQuality/100)."
                }
            )
        }

        val separations = result.timeline.mapNotNull(::hipShoulderSeparation)
        val separationRange = separations.takeIf { it.size >= requiredSamples }
            ?.let { it.max() - it.min() }
        if (separationRange != null) {
            val rotationScore = when {
                separationRange >= 18.0 -> 90
                separationRange >= 12.0 -> 78
                separationRange >= 7.0 -> 58
                else -> 38
            }
            metricScores["Hip Rotation"] = rotationScore
            weightedScores += rotationScore to 25
            if (separationRange >= 12.0) {
                findings += Finding(
                    FindingType.GOOD, "Rotation sequence",
                    "Clear shoulder-to-hip separation change through the swing — your lower and upper body are not moving as one rigid block."
                )
            } else {
                findings += Finding(
                    FindingType.ISSUE, "Rotation sequence",
                    "Limited shoulder-to-hip separation change was detected, so the visible swing may be relying too much on the arms."
                )
                findings += Finding(
                    FindingType.TIP, "Rotation sequence",
                    "Start the swing by rotating your hips, then let your shoulders and hands follow."
                )
            }
        } else {
            missingAreas += "rotation sequence"
        }

        val headDrift = battingHeadDrift(result.timeline, requiredSamples)
        if (headDrift != null) {
            val headStabilityScore = when {
                headDrift <= 0.20 -> 90
                headDrift <= 0.28 -> 80
                headDrift <= 0.42 -> 58
                else -> 36
            }
            metricScores["Ball Tracking"] = headStabilityScore
            weightedScores += headStabilityScore to 15
            if (headDrift <= 0.28) {
                findings += Finding(
                    FindingType.GOOD, "Head stability",
                    "Your head stays stable relative to your hips through the tracked swing. This is a stability proxy, not measured gaze or ball flight."
                )
            } else {
                findings += Finding(
                    FindingType.ISSUE, "Head stability",
                    "Your head shifts noticeably relative to your hips during the swing, which can make the movement harder to repeat."
                )
                findings += Finding(
                    FindingType.TIP, "Head stability",
                    "Use slow dry swings and keep your head centered between your feet through the hitting zone."
                )
            }
        } else {
            missingAreas += "head stability"
        }

        val peakHandFrame = peakTwoHandSpeedFrame(result.timeline)
        val armExtension = peakHandFrame?.let { frame ->
            listOfNotNull(elbowAngle(frame, right = false), elbowAngle(frame, right = true)).maxOrNull()
        }
        if (armExtension != null) {
            val extensionScore = when {
                armExtension >= 155.0 -> 90
                armExtension >= 145.0 -> 78
                armExtension >= 125.0 -> 58
                else -> 42
            }
            metricScores["Swing Extension"] = extensionScore
            weightedScores += extensionScore to 20
            if (armExtension >= 150.0) {
                findings += Finding(
                    FindingType.GOOD, "Extension",
                    "Good extension in the most extended arm during the peak hand-speed window — your hands continue through the visible hitting zone."
                )
            } else {
                findings += Finding(
                    FindingType.ISSUE, "Extension",
                    "The most extended arm remains bent in the peak hand-speed window (~${armExtension.toInt()}°), shortening the visible extension."
                )
                findings += Finding(
                    FindingType.TIP, "Extension",
                    "Drive both hands through the hitting zone, then allow natural extension into the finish."
                )
            }
        } else {
            missingAreas += "arm extension"
        }

        val kneeFlexes = result.timeline.mapNotNull(::shootingKneeFlexion)
        val maxKneeFlex = kneeFlexes.takeIf { it.size >= requiredSamples }?.maxOrNull()
        if (maxKneeFlex != null) {
            val lowerBodyScore = when {
                maxKneeFlex in 15.0..55.0 -> 82
                maxKneeFlex < 15.0 -> 35
                else -> 55
            }
            metricScores["Lower-Body Load"] = lowerBodyScore
            weightedScores += lowerBodyScore to 15
            if (maxKneeFlex in 15.0..55.0) {
                findings += Finding(
                    FindingType.GOOD, "Lower body",
                    "Visible knee bend creates an athletic loading base in the tracked swing."
                )
            } else if (maxKneeFlex < 15.0) {
                findings += Finding(
                    FindingType.ISSUE, "Lower body",
                    "Very little knee bend was visible across enough tracked frames."
                )
                findings += Finding(
                    FindingType.TIP, "Lower body",
                    "Get into an athletic stance with soft knees so you can load and drive."
                )
            }
        } else {
            missingAreas += "lower-body load"
        }

        val batSpeedPotential = battingHandSpeedScore(result.timeline)
        if (batSpeedPotential != null) {
            metricScores["Bat Speed Potential"] = batSpeedPotential
            weightedScores += batSpeedPotential to 25
            findings += if (batSpeedPotential >= 72) {
                Finding(
                    FindingType.GOOD, "Hand speed",
                    "Strong coordinated two-hand movement was visible relative to body size. This is movement potential, not measured bat speed."
                )
            } else {
                Finding(
                    FindingType.TIP, "Hand speed",
                    "Build speed gradually with controlled tee swings while keeping both hands connected through rotation. This score is not radar-measured bat speed."
                )
            }
        } else {
            missingAreas += "two-hand speed"
        }

        if (missingAreas.isNotEmpty()) {
            findings += Finding(
                FindingType.TIP,
                "Visibility",
                "Not measured from this clip: ${missingAreas.joinToString()}. SportsAI left these areas unscored instead of treating missing joints as poor technique."
            )
        }
        if (result.detectionRate < 0.6f) {
            findings += Finding(
                FindingType.TIP, "Filming",
                "Only part of the batter was consistently visible. A side-on, full-body angle with good lighting improves offline accuracy."
            )
        }

        val score = weightedAverage(weightedScores)
        val partialScore = metricScores.size < Sport.BASEBALL_BAT.metrics.size
        val overview = buildOverview(sport, score, metricScores, partialScore)
        val summary = buildString {
            append("Batter Lock analyzed one continuous athlete track. ")
            append(if (metricScores.isEmpty()) {
                "The visible joints were not complete enough for mechanics scores."
            } else if (partialScore) {
                "The $score/100 value averages only ${metricScores.size} visible measured areas; it is not a complete overall rating."
            } else {
                buildSummary(score)
            })
        }
        return TechniqueReport(
            sport = sport,
            overallScore = score,
            summary = summary,
            findings = findings,
            detectionRate = result.detectionRate,
            metricScores = metricScores,
            aiOverview = overview
        )
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

    private fun buildOverview(
        sport: String,
        score: Int,
        metrics: Map<String, Int>,
        partialScore: Boolean = false
    ): String {
        if (metrics.isEmpty()) {
            return "Batter Lock followed one athlete, but the clip did not show enough reliable joints for individual mechanics scores. " +
                "SportsAI left those areas unmeasured instead of turning missing landmarks into technique faults. " +
                "Record the full body and both hands from setup through follow-through. " +
                "Analyze the clearer clip to create a trustworthy offline baseline."
        }
        val best = metrics.maxByOrNull { it.value }
        val worst = metrics.minByOrNull { it.value }
        val rating = when {
            score >= 80 -> "excellent"
            score >= 60 -> "solid"
            else -> "developing"
        }
        return buildString {
            if (partialScore) {
                append("Across the ${metrics.size} visible areas SportsAI could measure, your $sport average is $score/100; this is not a complete overall rating. ")
            } else {
                append("Your $sport technique scores $score/100 overall, showing $rating form. ")
            }
            if (best != null) {
                append("${overviewMetricLabel(sport, best.key)} is currently your strongest area at ${best.value}/100. ")
            }
            if (worst != null && worst.key != best?.key) {
                append("Your clearest opportunity is ${overviewMetricLabel(sport, worst.key)}, which scored ${worst.value}/100. ")
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
            val bodyScale = battingBodyScale(current).coerceAtLeast(1.0)

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

    /** Signed pelvis/shoulder axial separation; unavailable when depth is not reliable. */
    private fun hipShoulderSeparation(frame: FramePose): Double? {
        val leftShoulder = frame.point(LEFT_SHOULDER) ?: return null
        val rightShoulder = frame.point(RIGHT_SHOULDER) ?: return null
        val leftHip = frame.point(LEFT_HIP) ?: return null
        val rightHip = frame.point(RIGHT_HIP) ?: return null
        // Never subtract angles from different planes. Partial depth is common when the catcher
        // occludes the batter's hips and would otherwise create false separation.
        val shoulderWidth = distance2d(leftShoulder, rightShoulder).coerceAtLeast(1.0)
        val hipWidth = distance2d(leftHip, rightHip).coerceAtLeast(1.0)
        val hasReliableDepth = abs(rightShoulder.z - leftShoulder.z) >= shoulderWidth * 0.04 &&
            abs(rightHip.z - leftHip.z) >= hipWidth * 0.04
        if (!hasReliableDepth) return null
        val shoulderAngle = depthLineOrientation(leftShoulder, rightShoulder)
        val hipAngle = depthLineOrientation(leftHip, rightHip)
        return signedAngleDifference(shoulderAngle, hipAngle)
    }

    private fun depthLineOrientation(
        left: LandmarkPoint,
        right: LandmarkPoint
    ): Double = Math.toDegrees(
        atan2((right.z - left.z).toDouble(), (right.x - left.x).toDouble())
    )

    private fun overviewMetricLabel(sport: String, metric: String): String =
        if (sport == Sport.BASEBALL_BAT.displayName) {
            when (metric) {
                "Ball Tracking" -> "Head stability (the on-device ball-tracking proxy)"
                "Hip Rotation" -> "Rotation sequencing (the on-device hip-rotation proxy)"
                "Swing Extension" -> "Arm extension near peak hand speed"
                "Lower-Body Load" -> "Lower-body load (the on-device knee-flexion proxy)"
                else -> metric
            }
        } else {
            metric
        }

    private fun weightedAverage(values: List<Pair<Int, Int>>): Int {
        val totalWeight = values.sumOf { it.second }
        if (totalWeight <= 0) return 0
        return (values.sumOf { (value, weight) -> value * weight }.toDouble() / totalWeight)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun distance2d(first: LandmarkPoint, second: LandmarkPoint): Double =
        hypot((second.x - first.x).toDouble(), (second.y - first.y).toDouble())

    private fun angleDifference(first: Double, second: Double): Double {
        val difference = abs(first - second) % 360.0
        return minOf(difference, 360.0 - difference)
    }

    private fun signedAngleDifference(first: Double, second: Double): Double {
        var difference = (first - second) % 360.0
        if (difference > 180.0) difference -= 360.0
        if (difference < -180.0) difference += 360.0
        return difference
    }

    /** Range of head position relative to pelvis, normalized by torso/shoulder size. */
    private fun battingHeadDrift(timeline: List<FramePose>, requiredSamples: Int): Double? {
        val positions = timeline.mapNotNull { frame ->
            val leftHip = frame.point(LEFT_HIP) ?: return@mapNotNull null
            val rightHip = frame.point(RIGHT_HIP) ?: return@mapNotNull null
            val head = frame.point(0)
            val leftShoulder = frame.point(LEFT_SHOULDER)
            val rightShoulder = frame.point(RIGHT_SHOULDER)
            val headX = head?.x ?: if (leftShoulder != null && rightShoulder != null) {
                (leftShoulder.x + rightShoulder.x) / 2f
            } else return@mapNotNull null
            val headY = head?.y ?: (leftShoulder!!.y + rightShoulder!!.y) / 2f
            val hipX = (leftHip.x + rightHip.x) / 2f
            val hipY = (leftHip.y + rightHip.y) / 2f
            val scale = battingBodyScale(frame).coerceAtLeast(1.0)
            (headX - hipX) / scale to (headY - hipY) / scale
        }
        if (positions.size < requiredSamples) return null
        val xRange = positions.maxOf { it.first } - positions.minOf { it.first }
        val yRange = positions.maxOf { it.second } - positions.minOf { it.second }
        return hypot(xRange.toDouble(), yRange.toDouble())
    }

    private fun peakTwoHandSpeedFrame(timeline: List<FramePose>): FramePose? {
        val peakIndex = coordinatedBattingPeakIndex(timeline) ?: return null
        return timeline.getOrNull(peakIndex)
    }

    private fun battingHandSpeedScore(timeline: List<FramePose>): Int? {
        val peakIndex = coordinatedBattingPeakIndex(timeline) ?: return null
        val intervalSpeeds = coordinatedBattingMotionScores(timeline)
        val peakWindow = intervalSpeeds.subList(peakIndex - 1, peakIndex + 2)
        val peakMedian = peakWindow.sorted()[1]
        return (35.0 + peakMedian * 11.0).roundToInt().coerceIn(35, 96)
    }

    private fun battingBodyScale(frame: FramePose): Double {
        val leftShoulder = frame.point(LEFT_SHOULDER)
        val rightShoulder = frame.point(RIGHT_SHOULDER)
        val leftHip = frame.point(LEFT_HIP)
        val rightHip = frame.point(RIGHT_HIP)
        val shoulderWidth = if (leftShoulder != null && rightShoulder != null) {
            hypot(
                (rightShoulder.x - leftShoulder.x).toDouble(),
                (rightShoulder.y - leftShoulder.y).toDouble()
            )
        } else 0.0
        val torso = if (leftShoulder != null && rightShoulder != null &&
            leftHip != null && rightHip != null
        ) {
            val shoulderX = (leftShoulder.x + rightShoulder.x) / 2f
            val shoulderY = (leftShoulder.y + rightShoulder.y) / 2f
            val hipX = (leftHip.x + rightHip.x) / 2f
            val hipY = (leftHip.y + rightHip.y) / 2f
            hypot((hipX - shoulderX).toDouble(), (hipY - shoulderY).toDouble())
        } else 0.0
        return max(shoulderWidth, torso * 0.8)
    }

    private fun elbowAngle(frame: FramePose, right: Boolean): Double? {
        val shoulder = frame.point(if (right) RIGHT_SHOULDER else LEFT_SHOULDER) ?: return null
        val elbow = frame.point(if (right) RIGHT_ELBOW else LEFT_ELBOW) ?: return null
        val wrist = frame.point(if (right) RIGHT_WRIST else LEFT_WRIST) ?: return null
        return angle(shoulder, elbow, wrist)
    }

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
