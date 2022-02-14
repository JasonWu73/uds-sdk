package com.qgschina.udssdk.common.exception;

/**
 * UDS SDK 所有自定义异常的基类
 */
public class UdsSdkException extends RuntimeException {

  public UdsSdkException() {
  }

  public UdsSdkException(String message) {
    super(message);
  }

  public UdsSdkException(String message, Throwable cause) {
    super(message, cause);
  }
}
