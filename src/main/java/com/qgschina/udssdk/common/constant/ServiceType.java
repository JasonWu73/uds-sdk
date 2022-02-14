package com.qgschina.udssdk.common.constant;

import lombok.RequiredArgsConstructor;

/**
 * 注册微服务时所使用的顶级命名空间
 */
@RequiredArgsConstructor
public enum ServiceType {

  /**
   * 平台相关
   */
  PLATFORM_CONTROLLER("net.hjxinxi.controller"),

  /**
   * 平台缓存
   */
  PLATFORM_CACHE("net.hjxinxi.cache"),

  /**
   * 基础功能
   */
  CORE("net.hjxinxi.core"),

  /**
   * 第三方对接
   */
  THIRD("net.hjxinxi.third"),

  /**
   * 远程操作
   */
  REMOTE("net.hjxinxi.remote"),

  /**
   * Office 操作
   */
  OFFICE("net.hjxinxi.office"),

  /**
   * 法庭类设备操作
   */
  COURT("net.hjxinxi.court"),

  /**
   * 自定义
   */
  CUSTOM("net.hjxinxi.custom");

  public static final String SOCKET_FILE_DIR = "/tmp/";

  private final String value;

  public String value() {
    return SOCKET_FILE_DIR + value;
  }
}
