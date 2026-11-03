package chatsgate.slackfx.core;

import datapotter.datahelper.Data;

import module java.base;

@Data
public final class Cache extends Cache_A {
    Map<String, String> users = new LinkedHashMap<>();
    Map<String, String> handles = new LinkedHashMap<>();
    Map<String, String> channels = new LinkedHashMap<>();
}
