package com.qgschina.udssdk.common.constant;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum InnerParamType {

  /**
   * 字符串 ({@code String} 或 {@code byte[]})
   */
  STRING("String"),

  /**
   * 数字 ({@code long} 或 {@code int})
   */
  INT("int"),

  /**
   * 浮点数 ({@code double} 或 {@code float})
   */
  FLOAT("float"),

  /**
   * 布尔值
   */
  BOOL("bool"),

  /**
   * 字典 ({@code Map}) 或 POJO
   */
  MAP("Map"),

  /**
   * 列表 ({@code List})
   */
  LIST("List");

  private final String value;

  public String value() {
    return value;
  }
}
