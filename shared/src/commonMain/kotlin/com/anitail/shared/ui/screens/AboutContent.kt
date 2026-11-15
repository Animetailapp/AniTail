/**
 * AniTail Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.anitail.shared.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.anitail.shared.generated.resources.Res
import com.anitail.shared.generated.resources.beta_testers
import com.anitail.shared.generated.resources.developer_about
import com.anitail.shared.generated.resources.info_dev
import com.anitail.shared.generated.resources.links_about
import com.anitail.shared.generated.resources.my_channel
import com.anitail.shared.generated.resources.my_channel_info
import com.anitail.shared.generated.resources.other_apps
import com.anitail.shared.generated.resources.other_apps_info
import com.anitail.shared.generated.resources.patreon
import com.anitail.shared.generated.resources.patreon_info
import com.anitail.shared.ui.icons.AnitailIconsBrand
import org.jetbrains.compose.resources.stringResource

sealed interface AboutItem {
    val id: String
}

data class HeaderItem(
    val buildInfo: BuildInfo,
) : AboutItem {
    override val id: String = "header"
}

data class SectionItem(
    override val id: String,
    val title: String,
    val icon: ImageVector,
    val items: List<CardItem>,
) : AboutItem

data class TeamSectionItem(
    override val id: String,
    val title: String,
    val icon: ImageVector,
    val members: List<TeamMember>,
) : AboutItem

data class BuildInfo(
    val buildType: String,
    val versionName: String,
    val deviceInfo: String,
)

data class CardItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val url: String,
)

data class TeamMember(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val role: String,
    val url: String,
)

internal object AboutIcons {
    val Build: ImageVector by lazy {
        vectorIcon(
            name = "AboutBuild",
            pathData =
                "M22.7,19l-9.1,-9.1c0.9,-2.3 0.4,-5 -1.5,-6.9 -2,-2 -5,-2.4 -7.4,-1.3L9,6 6,9 1.2,4.7c-1.1,2.4 -0.7,5.4 1.3,7.4 1.9,1.9 4.6,2.4 6.9,1.5l9.1,9.1c0.4,0.4 1,0.4 1.4,0l2.3,-2.3c0.5,-0.4 0.5,-1.1 0.1,-1.4z",
        )
    }

    val Verified: ImageVector by lazy {
        vectorIcon(
            name = "AboutVerified",
            pathData =
                "M23,12l-2.44,-2.79 0.34,-3.69 -3.61,-0.82L15.4,1.5 12,2.96 8.6,1.5 6.71,4.69 3.1,5.5l0.34,3.7L1,12l2.44,2.79 -0.34,3.7 3.61,0.82L8.6,22.5l3.4,-1.47 3.4,1.46 1.89,-3.19 3.61,-0.82 -0.34,-3.69L23,12zM10.09,16.72l-3.8,-3.81 1.48,-1.48 2.32,2.33 5.85,-5.87 1.48,1.48 -7.33,7.35z",
        )
    }

    val Devices: ImageVector by lazy {
        vectorIcon(
            name = "AboutDevices",
            pathData =
                "M22,9V7h-2V5c0,-1.1 -0.9,-2 -2,-2H4c-1.1,0 -2,0.9 -2,2v6c0,1.1 0.9,2 2,2h2v2c0,1.1 0.9,2 2,2h2v3l3,-3h5c1.1,0 2,-0.9 2,-2v-2h2v-2h-2zM8,13H4V5h14v2H8c-1.1,0 -2,0.9 -2,2v4z",
        )
    }

    val Links: ImageVector by lazy {
        vectorIcon(
            name = "AboutLinks",
            pathData =
                "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4L11,7L7,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9L7,15.1c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2L8,11v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4L13,17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z",
        )
    }

    val BabelSoftwareApps: ImageVector by lazy {
        vectorIcon(
            name = "AboutBabelSoftwareApps",
            pathData =
                "M4,8h4L8,4L4,4v4zM10,20h4v-4h-4v4zM4,20h4v-4L4,16v4zM4,14h4v-4L4,10v4zM10,14h4v-4h-4v4zM16,4v4h4L20,4h-4zM10,8h4L14,4h-4v4zM16,14h4v-4h-4v4zM16,20h4v-4h-4v4z",
        )
    }

    val Patreon: ImageVector by lazy {
        vectorIcon(
            name = "AboutPatreon",
            viewport = 512f,
            pathData =
                "M489.7 153.8c-0.1 -65.4 -51 -119 -110.7 -138.3C304.8 -8.5 207 -5 136.1 28.4C50.3 68.9 23.3 157.7 22.3 246.2C21.5 319 28.7 510.6 136.9 512c80.3 1 92.3 -102.5 129.5 -152.3c26.4 -35.5 60.5 -45.5 102.4 -55.9c72 -17.8 121.1 -74.7 121 -150z",
        )
    }

    val Person: ImageVector by lazy {
        vectorIcon(
            name = "AboutPerson",
            fillType = PathFillType.EvenOdd,
            pathData =
                "M12,4C9.79,4 8,5.79 8,8C8,10.21 9.79,12 12,12C14.21,12 16,10.21 16,8C16,5.79 14.21,4 12,4ZM14,8C14,6.9 13.1,6 12,6C10.9,6 10,6.9 10,8C10,9.1 10.9,10 12,10C13.1,10 14,9.1 14,8ZM18,18C17.8,17.29 14.7,16 12,16C9.31,16 6.23,17.28 6,18H18ZM4,18C4,15.34 9.33,14 12,14C14.67,14 20,15.34 20,18V20H4V18Z",
        )
    }

    val OpenInNew: ImageVector by lazy {
        vectorIcon(
            name = "AboutOpenInNew",
            pathData =
                "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z",
        )
    }

    private fun vectorIcon(
        name: String,
        pathData: String,
        viewport: Float = 24f,
        fillType: PathFillType = PathFillType.NonZero,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = viewport,
                viewportHeight = viewport,
            ).apply {
                addPath(
                    pathData = PathParser().parsePathString(pathData).toNodes(),
                    pathFillType = fillType,
                    fill = SolidColor(Color.Black),
                )
            }.build()
}

@Composable
fun createDefaultAboutItems(buildInfo: BuildInfo): List<AboutItem> =
    listOf(
        HeaderItem(buildInfo),
        SectionItem(
            id = "links",
            title = stringResource(Res.string.links_about),
            icon = AboutIcons.Links,
            items =
                listOf(
                    CardItem(
                        title = stringResource(Res.string.my_channel),
                        subtitle = stringResource(Res.string.my_channel_info),
                        icon = AnitailIconsBrand.Discord,
                        url = "https://discord.gg/fvskrQZb9j",
                    ),
                    CardItem(
                        title = stringResource(Res.string.other_apps),
                        subtitle = stringResource(Res.string.other_apps_info),
                        icon = AboutIcons.BabelSoftwareApps,
                        url = "https://github.com/Animetailapp",
                    ),
                    CardItem(
                        title = stringResource(Res.string.patreon),
                        subtitle = stringResource(Res.string.patreon_info),
                        icon = AboutIcons.Patreon,
                        url = "https://www.patreon.com/abydev",
                    ),
                ),
        ),
        TeamSectionItem(
            id = "dev",
            title = stringResource(Res.string.developer_about),
            icon = AboutIcons.Person,
            members =
                listOf(
                    TeamMember(
                        id = "dev",
                        name = "[̲̅A̲̅][̲̅b̲̅][̲̅y̲̅]",
                        role = stringResource(Res.string.info_dev),
                        avatarUrl = "https://avatars.githubusercontent.com/u/21024973?v=4",
                        url = "https://github.com/Dark25",
                    ),
                ),
        ),
        TeamSectionItem(
            id = "testers",
            title = stringResource(Res.string.beta_testers),
            icon = AboutIcons.Person,
            members =
                listOf(
                    TeamMember(
                        id = "t1",
                        name = "im.shoul",
                        role = "Beta Tester",
                        avatarUrl = "https://i.ibb.co/pjkzBvGn/image.png",
                        url = "https://discord.com/users/237686500567810058",
                    ),
                    TeamMember(
                        id = "t2",
                        name = "Lucia (Lú)",
                        role = "Beta Tester",
                        avatarUrl = "https://i.ibb.co/DPjf5V78/61fc6cc5422936a8fd81a913fbdf773b.png",
                        url = "https://discord.com/users/553307420688908320",
                    ),
                    TeamMember(
                        id = "t3",
                        name = "ElDeLasTojas",
                        role = "Beta Tester",
                        avatarUrl = "https://i.ibb.co/mrgvc1nb/14f2a18fa8b9e553a048027375db5f81.png",
                        url = "https://discord.com/users/444680132393697291",
                    ),
                    TeamMember(
                        id = "t4",
                        name = "SKHLORDKIRA",
                        role = "Beta Tester",
                        avatarUrl = "https://i.ibb.co/gnXkhnJ/be81ecb723cfd4186e85bfe81793f594.png",
                        url = "https://discord.com/users/445310321717018626",
                    ),
                    TeamMember(
                        id = "t5",
                        name = "Abyss",
                        role = "Beta Tester",
                        avatarUrl = "https://i.ibb.co/TDdPq2jF/0f0f47f2a47eca3eda2a433237b4a05d.png",
                        url = "https://discord.com/users/341662495301304323",
                    ),
                    TeamMember(
                        id = "t6",
                        name = "Jack",
                        role = "Beta Tester",
                        avatarUrl = "https://i.ibb.co/3YPX1wsj/dec881377d42d58473b6d988165406b6.png",
                        url = "https://discord.com/users/1166985299885309954",
                    ),
                    TeamMember(
                        id = "t7",
                        name = "R4fa3l_2008",
                        role = "Beta Tester",
                        avatarUrl = "https://i.ibb.co/htmds91/b514910877f4b585309265fbe922f020.png",
                        url = "https://discord.com/users/1318948121782521890",
                    ),
                    TeamMember(
                        id = "t8",
                        name = "Ryak",
                        role = "Beta Tester",
                        avatarUrl = "https://i.ibb.co/mrwz7J7K/165cbedbd96ae35c2489286c8db9777d.png",
                        url = "https://discord.com/users/1075797587770228856",
                    ),
                    TeamMember(
                        id = "t9",
                        name = "LucianRC",
                        role = "Beta Tester",
                        avatarUrl = "https://i.ibb.co/LXXWGJCt/e8cdcf2c32ee7056806c5a8bfa607830.png",
                        url = "https://discord.com/users/420641532446769157",
                    ),
                    TeamMember(
                        id = "t10",
                        name = "Alexx",
                        role = "Beta Tester",
                        avatarUrl = "https://i.ibb.co/8Dc1f67r/image.png",
                        url = "https://discord.com/users/743896907184734268",
                    ),
                ),
        ),
    )
