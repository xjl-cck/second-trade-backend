package org.example.secondhandtradingplatform.chat.dto;

import java.util.List;

/**
 * 重连补发的未读消息。
 *
 * <p>补发是 at-least-once，客户端需要按消息 id 去重；这里额外给出 {@code truncated}，
 * 表示未读数量超过单次补发上限，只推了最近的一批，更早的消息由客户端打开会话时按历史拉取。</p>
 */
public record ChatReplayResponse(boolean truncated, List<ChatMessageResponse> messages) {
}
