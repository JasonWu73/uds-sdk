package com.qgschina.udssdk.common.exception;

/**
 * 数据处理错误
 */
public class DataProcessException extends UdsSdkException {

  public DataProcessException() {
  }

  public DataProcessException(String message) {
    super(message);
  }

  public DataProcessException(String message, Throwable cause) {
    super(message, cause);
  }
}
