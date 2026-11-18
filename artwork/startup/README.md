来自参考仓库assets/icons/pages/home及operation_method；首页及作业方式背景原图拷贝至res/drawable-nodpi/startup_*。首页图标转换为Android矢量路径并按界面着色；作业图标原始SVG保留。本轮仅页面导航，其他按钮统一暂不支持。

Figma主页优化：以10 Screens/主页/Home-Connected（471:18513）为基准，实际使用figma_home_*本地资源，节点/文件/原始尺寸/SHA见figma-home-assets.json。连接图标原始SVG保留，使用librsvg按原始尺寸转透明PNG，不重绘路径；其余使用Figma导出的独立图标PNG及原始背景。临时下载URL不进入运行代码或文档。
