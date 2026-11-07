# 無垠伺服器 Android App

這個 repository 專門建置「Minecraft 無垠伺服器」Android Web App。App 使用原生 Android 外框與 WebView 載入官方網站，並支援 Google 登入回跳、語音麥克風權限與 Firebase Cloud Messaging（需自行設定 Firebase）。

- 網站：https://bols.dpdns.org/
- API：https://auth.bols.dpdns.org/
- Android applicationId：org.bols.minecraft
- 最低 Android 版本：Android 7.0 / API 24

## 介面

採用深色 Minecraft 科技風：深青黑背景、翡翠綠品牌標記、圓角網頁容器，以及首頁／返回／重新整理／外部瀏覽器導覽列。網站頁面仍由官方網站提供；原生 App 外框與網站內容分開維護。

## GitHub Actions 自動建置

每次推送到 main，或在 Actions 手動執行 Build Android APK，都會自動建置 Debug APK 並上傳為 Artifact。

1. 開啟 repository 的 Actions。
2. 選擇最新的 Build Android APK 執行。
3. 等待工作完成。
4. 在該次執行頁面下載 BolsMinecraft-debug-apk Artifact，解壓縮取得 app-debug.apk。

## Firebase（可選）

google-services.json 已加入 .gitignore，不會提交到公開 repository。

若要讓 GitHub Actions 建置的 APK 啟用 FCM：

1. 從 Firebase Console 下載 Android App 專用的 google-services.json（套件名稱必須是 org.bols.minecraft）。
2. 將檔案內容轉成 Base64。
3. 在 GitHub repository 的 Settings → Secrets and variables → Actions 新增 Repository secret：GOOGLE_SERVICES_JSON_BASE64，值為完整 Base64 字串。
4. 重新執行 Actions。工作流程會在建置期間暫時還原設定檔，不會提交到 repository。

若未設定 Secret，仍可建置一般網站 WebView APK，但原生 FCM 不會啟用。程式在沒有 Firebase 設定時會略過 FCM Token 註冊。

## Google 登入

App 會在使用者點擊網站 Google 登入時，開啟系統瀏覽器完成 OAuth。Worker 必須支援 Android 專用交接流程：

- 登入入口接受 ?client=android
- OAuth 完成後導向 bolsminecraft://auth/callback?code=...
- App 將一次性 code POST 到 https://auth.bols.dpdns.org/api/app-auth/exchange
- Worker 回傳工作階段後，App 將 session cookie 儲存至 WebView CookieManager

若 Worker 尚未部署對應路由，只更新 APK 無法單獨完成登入交接。

## 本機建置

需要 Android Studio、JDK 17、Android SDK Platform 35 與 Build Tools 35.0.0。專案使用 Android Gradle Plugin 8.7.3 與 Gradle 8.9。

APK 輸出位置：app/build/outputs/apk/debug/app-debug.apk

若要使用 FCM，請將自己的 google-services.json 放在 app/google-services.json，再執行 Gradle Sync 與建置。

## 安全與維護

- 不要提交 google-services.json、服務帳戶私鑰、VAPID 私鑰、OAuth Client Secret 或 Cloudflare Secrets。
- 不要在 Android 專案中放入 Worker 私密金鑰；後端 Secrets 應只存在 Cloudflare Worker。
- 更新 Android 網頁入口時，確認網站網域白名單、Google OAuth 回跳 URI 與 Worker App exchange API 一致。
- Actions 成功只代表 APK 編譯成功；實際登入、麥克風與推播仍需在裝置上測試。
