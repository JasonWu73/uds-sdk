package com.qgschina.udssdk.common.constant;

import lombok.RequiredArgsConstructor;

/**
 * 各语言 SDK 内部间传递的错误码
 */
@RequiredArgsConstructor
public enum InnerErrorCode {

  SUCCESS(0),

  ERROR(1);

  private final int value;

  public int value() {
    return value;
  }
}
