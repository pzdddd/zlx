package com.fuck.zlx

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 使用帮助说明页：只描述各页面功能与操作方法。
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {

        // --- 顶部栏 ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "使用帮助",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            HelpSection("快速上手")
            HelpText(
                "1. 在「首页」浏览视频网站（第一次打开默认加载 www.zl-x.com）。\n" +
                    "2. 浏览过程中应用会自动识别网页里视频的真实地址和封面，收进「资源」页。\n" +
                    "3. 到「资源」页点击视频即可在线播放，或点「下载」保存到手机。"
            )

            HelpSection("首页 · 网页浏览")
            HelpText(
                "· 内置浏览器，直接访问视频站；第一次使用默认打开 www.zl-x.com。\n" +
                    "· 右下角菜单功能：\n" +
                    "　刷新 —— 重新加载当前页面；\n" +
                    "　设为首页 —— 把当前浏览的页面设为以后启动时打开的地址；\n" +
                    "　修改首页地址 —— 手动输入一个新网址作为首页；\n" +
                    "　收藏此网址 —— 收藏当前页面（自动记录页面标题）；\n" +
                    "　查看收藏 —— 打开收藏列表，点任意条目直达对应页面。\n" +
                    "· 收藏列表第一行「默认主页」是内置条目，始终保留、不可删除；自己收藏的网址可以单条删除。\n" +
                    "· 网页下滑时底部导航栏会自动隐藏让阅读更清爽，上滑即可恢复。\n" +
                    "· 返回键：在网页历史间后退；没有历史时再按一次退出应用。"
            )

            HelpSection("资源页 · 嗅探结果")
            HelpText(
                "· 列表显示嗅探到的视频：封面、标题、大小、时长、发布日期。\n" +
                    "· 顶部「排序」菜单：正序/倒序切换、宫格视图/列表视图切换；切换页面后列表滚动位置会保留。\n" +
                    "· 「在线播放」按钮或直接点卡片：调起播放器播放（自动记入观看历史）。\n" +
                    "· 「下载」按钮：按设置的下载方式保存视频（见下载管理）。\n" +
                    "· 长按卡片：复制视频真实链接。\n" +
                    "· 右下角红色按钮：清空当前嗅探列表（清空后需重新浏览网页才会再次出现）。\n" +
                    "· 「观看历史」按钮：查看看过的视频，按时间倒序、最多保留 100 条；支持重新观看、单条删除、一键清空。"
            )

            HelpSection("播放器 · 手势操作")
            HelpText(
                "· 单击屏幕：显示/隐藏控制栏。\n" +
                    "· 双击屏幕左半边：快退 10 秒；双击右半边：快进 10 秒。\n" +
                    "· 长按屏幕：2 倍速播放，松手恢复正常速度。\n" +
                    "· 左半屏上下滑动：调节亮度。\n" +
                    "· 右半屏上下滑动：调节音量。\n" +
                    "· 左右拖动：拖动播放进度。\n" +
                    "· 控制栏内：播放/暂停、当前时间/总时长、实时网速、全屏切换。"
            )

            HelpSection("下载管理")
            HelpText(
                "· 从资源页顶部「查看下载」进入。\n" +
                    "· 进行中的任务显示进度和速度，完成后可直接播放。\n" +
                    "· 长按任务弹出菜单：打开、用其他应用打开、移动（导出/分享）、重命名、复制链接、重新下载、停止下载、删除、详细信息。\n" +
                    "· 详细信息包含：文件名、下载日期、文件路径、文件大小，方便定位下载好的视频。"
            )

            HelpSection("设置项说明")
            HelpText(
                "· 开启内置下载引擎：开启后用应用内多线程下载并自动转成 MP4；关闭后点下载会调用系统里的外部下载器。\n" +
                    "· 多线程并发数量：提高下载速度，数值过高可能导致手机发热。\n" +
                    "· 自定义下载目录：指定视频保存位置，默认系统下载文件夹。\n" +
                    "· 滑动加载视频：开启时网页滚动到哪加载到哪（省流防卡）；关闭时强制一次性加载全部隐藏视频（方便一次性提取）。\n" +
                    "· 悬浮底栏：开启为悬浮胶囊造型，关闭为贴底通栏。\n" +
                    "· 液态玻璃：底栏毛玻璃效果，关闭则使用纯色底栏、更省电。\n" +
                    "（底栏两项切换后需切换一次标签页生效）"
            )

            HelpSection("常见问题")
            HelpText(
                "· 资源页是空的？资源来自首页网页的自动嗅探，请先在首页浏览视频列表页。\n" +
                    "· 播放卡顿？多为网络或片源问题，可等待缓冲或换一个视频源。\n" +
                    "· 下载的视频存在哪？在下载管理长按任务 → 详细信息里查看文件路径；默认在系统下载文件夹。\n" +
                    "· 嗅探列表和观看历史的区别：嗅探列表是网页里发现的所有视频（可清空）；观看历史是你实际播放过的视频（自动记录）。"
            )
        }
    }
}

@Composable
private fun HelpSection(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun HelpText(content: String) {
    Text(
        text = content,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}
