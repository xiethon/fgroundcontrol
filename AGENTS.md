# 项目规则

- 表达简洁，只保留结论、关键依据和待办。
- 需求见 `docs/README.md`，未决事项见 `docs/05-decisions.md`。
- 界面设计参考 Figma，功能实现参考 `/Users/xiethon/fairland/fgroundcontrol`；节点、源码入口及采用边界见 `docs/20-reference-guide.md`。UI样式统一遵循 `docs/19-ui-tokens.md`。
- 始终遵循 `rules/engineering.md`；按任务阅读：
  - Android 开发：`rules/android.md`
  - 审查与交付：`rules/review.md`
  - 验证与缺陷：`rules/validation.md`
  - 模拟与 SITL：`rules/simulation.md`
  - 代码提交：`rules/git.md`
- 飞行行为未明确前不实现；构建命令以本仓库 README 为准。
- UI 视觉与交互由用户验证；AI 默认不启动 UI 或使用截图、界面自动化，交付简短验收步骤。
- 开始任务先读 `docs/07-progress.md`，按 `docs/06-tasks.md` 推进；结束时更新状态与证据。
