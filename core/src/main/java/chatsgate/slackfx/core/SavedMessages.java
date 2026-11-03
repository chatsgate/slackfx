package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.node.ObjectNode;

import module java.base;

/// What was saved for one conversation: its newest messages (oldest first), the names they mention, and whether Slack had older ones.
public record SavedMessages(List<ObjectNode> messages, Map<String, String> labels, boolean more) { }
