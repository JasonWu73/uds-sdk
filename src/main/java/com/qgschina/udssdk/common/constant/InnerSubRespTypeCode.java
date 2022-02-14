package com.qgschina.udssdk.common.constant;

import lombok.RequiredArgsConstructor;

/**
 * SDK Server 内部返回订阅类型
 */
@RequiredArgsConstructor
public enum InnerSubRespTypeCode {

  /**
   * 返回订阅是否成功的提示
   */
  SUB_MSG("subRes"),

  /**
   * 返回订阅的具体数据
   */
  SUB_DATA("subData");

  private final String value;

  public String value() {
    return value;
  }
}
