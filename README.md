# PackDroid

AndroidだけでMinecraft Java EditionのMODPACKを組み、3種類の形式へ書き出すMVPです。Minecraftを起動するランチャーではありません。

## 実装済み

- MinecraftバージョンとFabric / Forge / NeoForge / Quiltの指定
- Modrinth APIによるMOD検索
- 選択条件に合う最新releaseの取得
- jarのダウンロードとSHA-1 / SHA-512検証
- Modrinthの必須依存MODの再帰的な自動追加
- Androidのファイル選択画面からローカルjarを追加
- 編集内容の端末内自動保存
- 完全版ZIP
  - `mods/*.jar`
  - `packdroid.manifest.json`
- 軽量Manifest `.pdpack`
  - ZIP内に`packdroid.manifest.json`
  - Modrinth project ID / version ID / URL / SHA-1 / SHA-512 /環境情報を保存
  - jar本体は含まない
- Modrinth `.mrpack`
  - `modrinth.index.json`
  - Modrinth由来MODはダウンロードURLを保存
  - ローカルjarは`overrides/mods/`へ格納

## ビルド

Android Studioでこのフォルダを開き、JDK 17とAndroid SDK 35を設定して実行してください。

コマンドラインではGradle 8.9以上を用意して次を実行します。

```bash
gradle :app:assembleDebug
```

GitHubへ置いた場合は、同梱のGitHub Actionsから`PackDroid-debug-apk`を取得できます。

生成物:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 注意

- CurseForge APIは未実装です。
- `.pdpack`のインポート機能は未実装です。現在は書き出し専用です。
- Modrinthへの公開時は各MODやローカルjarの再配布許可を確認してください。
- ローダーバージョンは手入力です。
- APIリクエストには`User-Agent: sakusdev/PackDroid/0.1.0`を付けています。

## パッケージ

- Application ID: `dev.sakus.packdroid`
- Minimum Android: 8.0 / API 26
- Target Android: API 35
