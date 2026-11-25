package com.anitail.music.playback.video

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MusicVideoCounterpartTest {
    @Test
    fun parsesForwardAndReverseCounterpartWithSegmentMap() {
        val rawJson =
            """
            {
              "contents": {
                "singleColumnMusicWatchNextResultsRenderer": {
                  "playlist": {
                    "playlistPanelRenderer": {
                      "contents": [
                        {
                          "playlistPanelVideoWrapperRenderer": {
                            "primaryRenderer": {
                              "playlistPanelVideoRenderer": {
                                "videoId": "track-1"
                              }
                            },
                            "counterpart": [
                              {
                                "counterpartRenderer": {
                                  "playlistPanelVideoRenderer": {
                                    "videoId": "video-1",
                                    "navigationEndpoint": {
                                      "watchEndpoint": {
                                        "watchEndpointMusicSupportedConfigs": {
                                          "watchEndpointMusicConfig": {
                                            "musicVideoType": "MUSIC_VIDEO_TYPE_OMV"
                                          }
                                        }
                                      }
                                    }
                                  }
                                },
                                "segmentMap": {
                                  "segment": [
                                    {
                                      "primaryVideoStartTimeMilliseconds": "0",
                                      "counterpartVideoStartTimeMilliseconds": "12000",
                                      "durationMilliseconds": "60000"
                                    },
                                    {
                                      "primaryVideoStartTimeMilliseconds": "60000",
                                      "counterpartVideoStartTimeMilliseconds": "80000",
                                      "durationMilliseconds": "60000"
                                    }
                                  ]
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val root = Json.parseToJsonElement(rawJson).jsonObject

        val forward = parseMusicVideoCounterpart(root, "track-1")
        assertNotNull(forward)
        assertEquals("video-1", forward!!.targetVideoId)
        assertEquals("MUSIC_VIDEO_TYPE_OMV", forward.targetMusicVideoType)
        assertNotNull(forward.positionMap)
        assertEquals(22_000L, forward.positionMap!!.mapPosition(10_000L))
        assertEquals(90_000L, forward.positionMap!!.mapPosition(70_000L))

        val reverse = parseMusicVideoCounterpart(root, "video-1")
        assertNotNull(reverse)
        assertEquals("track-1", reverse!!.targetVideoId)
        assertNull(reverse.targetMusicVideoType)
        assertEquals(10_000L, reverse.positionMap!!.mapPosition(22_000L))
        assertEquals(70_000L, reverse.positionMap!!.mapPosition(90_000L))
        assertEquals(22_000L, reverse.positionMap!!.reversed().mapPosition(10_000L))
    }

    @Test
    fun ignoresSelfTargetOrMissingCounterparts() {
        val selfJson =
            """
            {
              "playlistPanelVideoWrapperRenderer": {
                "primaryRenderer": {
                  "playlistPanelVideoRenderer": {
                    "videoId": "same-id"
                  }
                },
                "counterpart": [
                  {
                    "counterpartRenderer": {
                      "playlistPanelVideoRenderer": {
                        "videoId": "same-id"
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent()

        val root = Json.parseToJsonElement(selfJson).jsonObject
        assertNull(parseMusicVideoCounterpart(root, "same-id"))
        assertNull(parseMusicVideoCounterpart(root, "other-id"))
    }
}

