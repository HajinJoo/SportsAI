package com.example.sportsai.data

import com.example.sportsai.model.LandmarkPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class BatterPoseSelectorTest {

    @Test
    fun selectsSwingingBatterWhenCatcherAndUmpireAreMoreProminent() {
        val frames = (0 until 12).map { index ->
            val poses = listOf(
                batterPose(index),
                catcherPose(index, movingGlove = true),
                umpirePose()
            )
            MultiPoseFrame(
                timestampMs = index * 200L,
                width = 1_000,
                height = 700,
                poses = if (index % 2 == 0) poses else listOf(poses[2], poses[0], poses[1])
            )
        }

        val result = BatterPoseSelector().select(frames)

        assertTrue(result.accepted)
        assertTrue(result.maxPeopleDetected == 3)
        assertTrue(result.posesByFrameIndex.size >= 10)
        result.posesByFrameIndex.forEach { (frameIndex, pose) ->
            val selectedLeftShoulder = pose.landmarks.first { it.type == 11 }
            val expectedLeftShoulder = batterPose(frameIndex).first { it.type == 11 }
            assertTrue(kotlin.math.abs(selectedLeftShoulder.x - expectedLeftShoulder.x) < 1f)
        }
    }

    @Test
    fun doesNotMistakeOneFastCatcherGloveForTwoHandSwing() {
        val frames = (0 until 10).map { index ->
            MultiPoseFrame(
                timestampMs = index * 200L,
                width = 1_000,
                height = 700,
                poses = listOf(catcherPose(index, movingGlove = true), batterPose(index))
            )
        }

        val result = BatterPoseSelector().select(frames)

        assertTrue(result.accepted)
        val selected = result.posesByFrameIndex.getValue(6).landmarks
        assertTrue(selected.first { it.type == 11 }.x < 400f)
    }

    @Test
    fun rejectsAmbiguousMultipleSwingTracksInsteadOfGuessing() {
        val frames = (0 until 10).map { index ->
            MultiPoseFrame(
                timestampMs = index * 200L,
                width = 1_000,
                height = 700,
                poses = listOf(
                    batterPose(index),
                    batterPose(index).map { it.copy(x = it.x + 350f) }
                )
            )
        }

        val result = BatterPoseSelector().select(frames)

        assertFalse(result.accepted)
        assertTrue(result.posesByFrameIndex.isEmpty())
    }

    @Test
    fun keepsSingleVisibleBatterWithoutInventingAnotherPerson() {
        val frames = (0 until 8).map { index ->
            MultiPoseFrame(
                timestampMs = index * 200L,
                width = 1_000,
                height = 700,
                poses = listOf(batterPose(index))
            )
        }

        val result = BatterPoseSelector().select(frames)

        assertTrue(result.accepted)
        assertTrue(result.maxPeopleDetected == 1)
    }

    @Test
    fun rejectsSingleStaticOfficialInsteadOfCallingVisibilityABattingSwing() {
        val frames = (0 until 8).map { index ->
            MultiPoseFrame(
                timestampMs = index * 100L,
                width = 1_000,
                height = 700,
                poses = listOf(umpirePose())
            )
        }

        val result = BatterPoseSelector().select(frames)

        assertFalse(result.accepted)
        assertTrue(result.posesByFrameIndex.isEmpty())
    }

    @Test
    fun rejectsSingleStaticCatcherAndNoSwingBatter() {
        listOf(catcherPose(index = 0, movingGlove = false), batterPose(index = 0)).forEach { pose ->
            val frames = (0 until 8).map { index ->
                MultiPoseFrame(
                    timestampMs = index * 100L,
                    width = 1_000,
                    height = 700,
                    poses = listOf(pose)
                )
            }

            val result = BatterPoseSelector().select(frames)

            assertFalse(result.accepted)
            assertTrue(result.posesByFrameIndex.isEmpty())
        }
    }

    @Test
    fun rejectsShortDetectionBurstAcrossMostlyEmptyFrames() {
        val frames = (0 until 12).map { index ->
            MultiPoseFrame(
                timestampMs = index * 100L,
                width = 1_000,
                height = 700,
                poses = if (index in 4..7) listOf(batterPose(index)) else emptyList()
            )
        }

        val result = BatterPoseSelector().select(frames)

        assertFalse(result.accepted)
        assertTrue(result.posesByFrameIndex.isEmpty())
    }

    @Test
    fun acceptsBriefSwingInsideLongStaticSetupAndFinish() {
        val frames = (0 until 40).map { index ->
            MultiPoseFrame(
                timestampMs = index * 100L,
                width = 1_000,
                height = 700,
                poses = listOf(batterPose(index))
            )
        }

        val result = BatterPoseSelector().select(frames)

        assertTrue(result.accepted)
        assertTrue(result.posesByFrameIndex.size == frames.size)
    }

    @Test
    fun rejectsWholeBodyTranslationButKeepsRelativeBatterSwing() {
        val movingOfficial = (0 until 12).map { index ->
            MultiPoseFrame(
                timestampMs = index * 100L,
                width = 1_000,
                height = 700,
                poses = listOf(umpirePose().map { it.copy(x = it.x + index * 30f) })
            )
        }
        val movingBatter = (0 until 12).map { index ->
            MultiPoseFrame(
                timestampMs = index * 100L,
                width = 1_000,
                height = 700,
                poses = listOf(batterPose(index).map { it.copy(x = it.x + index * 10f) })
            )
        }

        assertFalse(BatterPoseSelector().select(movingOfficial).accepted)
        assertTrue(BatterPoseSelector().select(movingBatter).accepted)
    }

    @Test
    fun rejectsStaticBatterDuringCameraZoomAndRoll() {
        val staticDepthPose = batterPose(0).map { point ->
            point.copy(z = (point.x - 340f) * 0.30f)
        }
        val frames = (0 until 12).map { index ->
            MultiPoseFrame(
                timestampMs = index * 100L,
                width = 1_000,
                height = 700,
                poses = listOf(
                    similarityTransform(
                        staticDepthPose,
                        scale = 1.0 + index * 0.025,
                        rotationRadians = index * 0.02,
                        translateX = index * 8.0,
                        translateY = index * -4.0
                    )
                )
            )
        }

        assertFalse(BatterPoseSelector().select(frames).accepted)
    }

    @Test
    fun keepsBatterTrackAcrossBriefCatcherOcclusion() {
        val frames = (0 until 12).map { index ->
            MultiPoseFrame(
                timestampMs = index * 100L,
                width = 1_000,
                height = 700,
                poses = buildList {
                    // The batter is briefly hidden after the verified swing burst. The same
                    // athlete should resume without being joined to the catcher track.
                    if (index !in 7..8) add(batterPose(index))
                    add(catcherPose(index, movingGlove = true))
                    add(umpirePose())
                }
            )
        }

        val result = BatterPoseSelector().select(frames)

        assertTrue(result.accepted)
        assertTrue(result.posesByFrameIndex.size == 10)
        assertTrue(result.posesByFrameIndex.keys.none { it in 7..8 })
    }

    @Test
    fun keepsIdentityWhenBatterAndCatcherCrossAndDetectionOrderChanges() {
        val frames = (0 until 12).map { index ->
            val batter = batterPose(index).map { it.copy(x = it.x + index * 18f) }
            val catcher = catcherPose(index, movingGlove = false)
                .map { it.copy(x = it.x - index * 17f) }
            val poses = if (index % 2 == 0) {
                listOf(batter, catcher, umpirePose())
            } else {
                listOf(umpirePose(), catcher, batter)
            }
            MultiPoseFrame(index * 100L, 1_000, 700, poses)
        }

        val result = BatterPoseSelector().select(frames)

        assertTrue(result.accepted)
        result.posesByFrameIndex.forEach { (frameIndex, selected) ->
            val expectedX = batterPose(frameIndex).first { it.type == 11 }.x + frameIndex * 18f
            assertTrue(kotlin.math.abs(selected.landmarks.first { it.type == 11 }.x - expectedX) < 1f)
        }
    }

    @Test
    fun isolatedTwoHandJittersAcrossOcclusionsDoNotBecomeOneSwing() {
        val frames = (0 until 30).map { index ->
            val base = umpirePose()
            val pose = if (index in setOf(5, 6, 14, 15, 23, 24)) {
                val jump = if (index % 2 == 0) 75f else 0f
                base.map { point ->
                    if (point.type == 15 || point.type == 16) point.copy(x = point.x + jump)
                    else point
                }
            } else {
                base.map { point ->
                    if (point.type == 15 || point.type == 16) point.copy(inFrameLikelihood = 0.1f)
                    else point
                }
            }
            MultiPoseFrame(index * 100L, 1_000, 700, listOf(pose))
        }

        val result = BatterPoseSelector().select(frames)

        assertFalse(result.accepted)
    }

    @Test
    fun catcherStandingWithBothHandsTogetherIsNotAcceptedAsTheBatter() {
        val frames = (0 until 12).map { index ->
            MultiPoseFrame(
                timestampMs = index * 100L,
                width = 1_000,
                height = 700,
                poses = listOf(twoHandCatcherRisePose(index), umpirePose())
            )
        }

        val result = BatterPoseSelector().select(frames)

        assertFalse(result.accepted)
        assertTrue(result.posesByFrameIndex.isEmpty())
        assertTrue(result.rawMotionEvidence >= 0.07f)
        assertTrue(result.transverseEvidence < 0.25f)
    }

    @Test
    fun acceptsFrontAngleSwingWhoseStrongestTravelIsInDepth() {
        val frames = (0 until 12).map { index ->
            MultiPoseFrame(
                timestampMs = index * 100L,
                width = 1_000,
                height = 700,
                poses = listOf(frontAngleBatterPose(index), catcherPose(index, movingGlove = true))
            )
        }

        val result = BatterPoseSelector().select(frames)

        assertTrue(result.accepted)
        assertTrue(result.rawMotionEvidence >= 0.07f)
        assertTrue(result.transverseEvidence >= 0.25f)
        val timeline = result.posesByFrameIndex.entries.sortedBy { it.key }.map { it.value }
        assertTrue(coordinatedBattingPeakIndex(timeline) != null)
    }

    @Test
    fun rejectsVerticalCatcherRiseWithSmallCoherentDepthNoise() {
        val frames = (0 until 12).map { index ->
            val phase = swingPhase(index)
            val catcher = twoHandCatcherRisePose(index).map { point ->
                if (point.type == 15 || point.type == 16) point.copy(z = phase * 5f) else point
            }
            MultiPoseFrame(index * 100L, 1_000, 700, listOf(catcher, umpirePose()))
        }

        val result = BatterPoseSelector().select(frames)

        assertFalse(result.accepted)
        assertTrue(result.transverseEvidence < 0.25f)
    }

    @Test
    fun cannotCombineVerticalRawBurstWithASeparateWeakTransverseJitter() {
        val frames = (0 until 14).map { index ->
            val horizontalJitter = ((index - 10).coerceAtLeast(0) * 6f)
            val pose = twoHandCatcherRisePose(index).map { point ->
                if (point.type == 15 || point.type == 16) {
                    point.copy(x = point.x + horizontalJitter)
                } else point
            }
            MultiPoseFrame(index * 100L, 1_000, 700, listOf(pose, umpirePose()))
        }

        val result = BatterPoseSelector().select(frames)

        assertFalse(result.accepted)
        assertTrue(result.posesByFrameIndex.isEmpty())
    }

    private fun batterPose(index: Int): List<LandmarkPoint> {
        val phase = swingPhase(index)
        val handTravel = phase * 180f
        return listOf(
            point(0, 340f, 130f),
            point(11, 300f, 220f), point(12, 390f, 220f),
            point(13, 340f + handTravel * 0.55f, 255f - phase * 20f),
            point(14, 385f + handTravel * 0.65f, 250f - phase * 25f),
            point(15, 402f + handTravel, 265f - phase * 55f),
            point(16, 424f + handTravel, 270f - phase * 55f),
            point(23, 315f + phase * 15f, 370f), point(24, 375f - phase * 15f, 370f),
            point(25, 300f, 505f), point(26, 390f, 505f),
            point(27, 290f, 650f), point(28, 405f, 650f)
        )
    }

    private fun frontAngleBatterPose(index: Int): List<LandmarkPoint> {
        val phase = swingPhase(index)
        return batterPose(0).map { point ->
            when (point.type) {
                13 -> point.copy(x = point.x + phase * 5f, z = phase * 95f)
                14 -> point.copy(x = point.x + phase * 7f, z = phase * 110f)
                15 -> point.copy(x = point.x + phase * 8f, z = phase * 180f)
                16 -> point.copy(x = point.x + phase * 10f, z = phase * 180f)
                23 -> point.copy(z = phase * 10f)
                24 -> point.copy(z = phase * -10f)
                else -> point
            }
        }
    }

    private fun swingPhase(index: Int): Float = when {
        index < 3 -> 0f
        index > 7 -> 1f
        else -> (index - 3) / 4f
    }

    private fun catcherPose(index: Int, movingGlove: Boolean): List<LandmarkPoint> {
        val gloveTravel = if (movingGlove) index * 18f else 0f
        return listOf(
            point(0, 535f, 285f),
            point(11, 475f, 365f), point(12, 595f, 365f),
            point(13, 465f, 430f), point(14, 610f, 430f),
            point(15, 450f + gloveTravel, 475f), point(16, 625f, 475f),
            point(23, 495f, 480f), point(24, 575f, 480f),
            point(25, 450f, 500f), point(26, 620f, 500f),
            point(27, 430f, 585f), point(28, 640f, 585f)
        )
    }

    private fun twoHandCatcherRisePose(index: Int): List<LandmarkPoint> {
        val phase = swingPhase(index)
        val bodyRise = phase * 70f
        val handRise = phase * 130f
        return catcherPose(index, movingGlove = false).map { point ->
            when (point.type) {
                13 -> point.copy(x = 520f, y = 430f - bodyRise - handRise * 0.45f)
                14 -> point.copy(x = 565f, y = 430f - bodyRise - handRise * 0.45f)
                15 -> point.copy(x = 535f, y = 475f - bodyRise - handRise)
                16 -> point.copy(x = 550f, y = 475f - bodyRise - handRise)
                else -> point.copy(y = point.y - bodyRise)
            }
        }
    }

    private fun umpirePose(): List<LandmarkPoint> = listOf(
        point(0, 555f, 80f, 0.999f),
        point(11, 455f, 185f, 0.999f), point(12, 655f, 185f, 0.999f),
        point(13, 435f, 315f, 0.999f), point(14, 675f, 315f, 0.999f),
        point(15, 455f, 455f, 0.999f), point(16, 655f, 455f, 0.999f),
        point(23, 485f, 390f, 0.999f), point(24, 625f, 390f, 0.999f),
        point(25, 470f, 535f, 0.999f), point(26, 640f, 535f, 0.999f),
        point(27, 460f, 675f, 0.999f), point(28, 650f, 675f, 0.999f)
    )

    private fun similarityTransform(
        pose: List<LandmarkPoint>,
        scale: Double,
        rotationRadians: Double,
        translateX: Double,
        translateY: Double
    ): List<LandmarkPoint> = pose.map { point ->
        val x = point.x - 500.0
        val y = point.y - 350.0
        point.copy(
            x = (x * scale * cos(rotationRadians) - y * scale * sin(rotationRadians) +
                500.0 + translateX).toFloat(),
            y = (x * scale * sin(rotationRadians) + y * scale * cos(rotationRadians) +
                350.0 + translateY).toFloat(),
            z = (point.z * scale).toFloat()
        )
    }

    private fun point(
        type: Int,
        x: Float,
        y: Float,
        confidence: Float = 0.96f
    ) = LandmarkPoint(type, x, y, confidence)
}
