# flyingjack-cloud 提交代码手册

## 仓库结构

`flyingjack-cloud` 是根聚合工程，下面四个目录都是独立的 Git 子模块（各自有自己的远程仓库）：

- `common-lib`
- `auth-service`
- `third-party-service`
- `wms-cashier`

提交代码时要清楚：你改的文件属于哪个仓库，就该在哪个仓库里单独 commit——**不要在根目录一把梭 `git add -A`**，根仓库和各子模块是完全独立的提交历史。

## 分支规则

- 所有开发都在 **`develop`** 分支进行，**禁止直接 commit 到 `main`**。
- `main` 只接受从 `develop` 合并过来的变更（发布时合并）。
- 这条规则对根仓库和全部四个子模块都适用。
- **提交前必须先确认分支**：

  ```bash
  cd <子模块目录>
  git branch --show-current   # 必须是 develop
  ```

## 日常提交步骤

1. 进入对应的子模块目录（或根目录），确认当前分支是 `develop`。
2. 只 `git add` 自己本次改动涉及的文件——如果同一个文件里混了别人正在进行的其他改动（常见于 `pom.xml` 这类共享配置文件），用 `git add -p` 按 hunk 挑着暂存，不要整文件一起提交。
3. 按 Conventional Commit 格式写提交信息：`类型(服务名:模块): 描述`，类型限定 `feat/fix/refactor/docs/chore/test/perf`。
4. 仓库根目录下也提供了 `/commit <服务名>` 这个 skill（`.claude/skills/submodule-commit`），可以针对单个子模块自动生成规范化的提交信息——但如果该子模块里还混着无关的未提交改动，仍然要先手动挑选暂存范围，再执行提交，不要直接全量 `git add .`。
5. 各子模块单独 `git push`；根仓库单独 `git push`。

## common-lib 版本号规则（重要，容易漏改）

`common-lib` 与三个服务（`auth-service` / `third-party-service` / `wms-cashier`）各自维护**独立的语义化版本号**，不再由根 `pom.xml` 统一管理：

- `common-lib/pom.xml` 自己声明版本，如 `1.0.0`。
- 三个服务在自己 `pom.xml` 里的 `common-lib` 依赖上显式 `<version>` pin 一个具体版本号，必须和 common-lib 当时的版本完全一致。

**common-lib 已发布到 GitHub Packages**（`https://maven.pkg.github.com/flyingjack-cloud/common-lib`），三个服务的 `pom.xml` 都配了对应的 `<repositories>` 指向这个地址。develop 和 main 分支的解析方式不一样：

| 分支 | common-lib 解析方式 |
|---|---|
| `develop` | CI 里 checkout common-lib develop 分支最新 HEAD，本地 `mvn install` 现装现用，不看版本号，也不发布 |
| `main` | common-lib 的 CI 会 `mvn deploy` 把 pom 里当前版本号真正发布到 GitHub Packages；消费方按自己 pom 里 pin 的版本号，直接从 Packages 解析，不再 checkout common-lib 源码 |

也就是说：

- 在 `develop` 上迭代时，不需要手动对齐版本号，CI 会自动拿 common-lib 最新代码本地现装。
- 但凡涉及往 `main` 发布（或者你想让某个服务真正锁定某个 common-lib 版本），**必须先手动把该服务 pom 里的 `common-lib` 依赖版本改成目标版本号**，否则 main 构建会去 Packages 拉一个还没发布过的版本而失败，或者悄悄继续用旧版本、看起来"发布了"但内容没变。
- common-lib 发布新版本的标准动作：改 `common-lib/pom.xml` 里的 `<version>`，合并到 `main` 后 CI 会自动打 tag（`v{version}`，已存在则跳过）并 `mvn deploy` 到 GitHub Packages。发布完成后，再手动去逐个更新下游服务 pom 里 pin 的版本号，提交、推送。

这一步目前是纯手动的（没有接 Renovate 之类的自动化工具),common-lib 一旦 bump 版本，别忘了同步检查三个下游服务的 pin 是否需要跟进更新。

### 本地开发者单独构建某个服务

如果只想单独 clone 某一个服务仓库（不带完整 root + submodules 的 reactor）来构建，Maven 会按 `reactor → 本地 .m2 缓存 → <repositories> 里的远程仓库` 这个优先级解析 `common-lib`。带着完整 reactor 构建时完全不受影响；单独构建某个服务、且本地没有对应版本缓存时，需要在自己的 `~/.m2/settings.xml` 里配置一个有 `read:packages` 权限的 GitHub 个人 PAT 作为凭证：

```xml
<servers>
    <server>
        <id>github</id>
        <username>你的GitHub用户名</username>
        <password>你的PAT（read:packages）</password>
    </server>
</servers>
```

注意：只有 `main` 上发布过的版本才能这样拉取——`develop` 上的改动不发布，若 pin 的版本还没被 deploy 过，单独构建会直接报错找不到（这也是预期行为，`develop` 阶段本来就该带着 reactor 一起构建）。
