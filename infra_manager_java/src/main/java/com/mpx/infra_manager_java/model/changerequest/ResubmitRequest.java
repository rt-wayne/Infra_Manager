package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：補件並重送的請求本文（S9 R1）。rowVerNo 是樂觀鎖版本；resubMemo 是補件說明（選填，上限 2000 字，
//           空白存 null）；form 是修改後的整份表單，與建草稿／編輯草稿共用 AppDraftRequest（其中的 rowVerNo 不看，
//           以外層為準）。採巢狀而非平坦結構：Jackson 對 record 的 @JsonUnwrapped 反序列化支援不完整，
//           且前端本來就把表單當一個物件在傳
// ============================================================

public record ResubmitRequest(Long rowVerNo, String resubMemo, AppDraftRequest form) {
}
