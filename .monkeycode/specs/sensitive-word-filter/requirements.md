# 需求文档：敏感词过滤

## Introduction

在 ChatClient 的 Advisor 链上对模型的输入与输出执行敏感词检查。用户输入命中敏感词时，系统直接返回固定失败响应且不调用底层模型；模型输出命中敏感词时，系统以失败响应替换原始输出并停止回答。本需求通过增强 Spring AI 现有 `SafeGuardAdvisor` 实现，同时引入可插拔的匹配策略，保证向后兼容。

## Glossary

- **敏感词（Sensitive Word）**：由配置方提供的、在用户输入或模型输出中不允许出现的词或短语。
- **匹配器（SensitiveWordMatcher）**：判定一段文本是否命中任一敏感词的策略组件，算法可替换。
- **失败响应（Failure Response）**：拦截命中后返回给调用方的固定消息文本。
- **输入检查（Input Filter）**：对发送给模型的 prompt 内容执行敏感词匹配。
- **输出检查（Output Filter）**：对模型生成的响应内容执行敏感词匹配。

## Requirements

### R1 输入检查

**User Story:** AS 应用开发者，I want 用户输入命中敏感词时直接拦截，SO THAT 敏感输入不会被发送给模型。

#### Acceptance Criteria

1. WHEN 用户输入包含任一敏感词，系统 SHALL 返回失败响应。
2. WHEN 用户输入包含任一敏感词，系统 SHALL 不调用底层模型。
3. WHEN 用户输入不包含敏感词，系统 SHALL 将请求传递给 Advisor 链中的下一环。
4. WHILE 执行输入检查，系统 SHALL 对每个敏感词执行忽略大小写与空白的子串匹配。

### R2 输出检查

**User Story:** AS 应用开发者，I want 模型输出命中敏感词时拦截并停止回答，SO THAT 敏感内容不会展示给用户。

#### Acceptance Criteria

1. WHEN 非流式（call）响应的完整文本包含任一敏感词，系统 SHALL 以失败响应替换原始响应。
2. WHEN 流式（stream）响应聚合后的完整文本包含任一敏感词，系统 SHALL 以失败响应替换整个输出流。
3. WHEN 响应文本不包含敏感词，系统 SHALL 原样传递响应。

### R3 匹配策略可插拔

**User Story:** AS 应用开发者，I want 匹配算法可替换，SO THAT 可按需切换子串、正则等不同检测策略。

#### Acceptance Criteria

1. 系统 SHALL 提供公开可替换的匹配器组件。
2. 系统 SHALL 提供默认匹配器，其执行忽略大小写与空白的子串匹配。

### R4 配置项

**User Story:** AS 应用开发者，I want 通过配置控制过滤行为，SO THAT 无需改动代码即可调整拦截策略。

#### Acceptance Criteria

1. 系统 SHALL 允许配置敏感词列表。
2. 系统 SHALL 允许配置失败响应文本。
3. 系统 SHALL 允许独立启用或停用输入检查与输出检查。
4. 系统 SHALL 允许配置 Advisor 在链中的执行顺序。

### R5 向后兼容

**User Story:** AS 现有 SafeGuardAdvisor 使用者，I want 升级后现有代码继续可用，SO THAT 不破坏既有应用。

#### Acceptance Criteria

1. 系统 SHALL 保持 `SafeGuardAdvisor` 既有公开构造方法可用。
2. 系统 SHALL 保持输入检查行为默认开启。
3. 系统 SHALL 保持默认失败响应文本与既有默认值一致。
