package com.qgschina.udssdk.common.exception;

/**
 * 参数或数据类型错误
 */
public class ParamException extends UdsSdkException {

  public ParamException() {
  }

  public ParamException(String message) {
    super(message);
  }

  public ParamException(String message, Throwable cause) {
    super(message, cause);
  }
}
