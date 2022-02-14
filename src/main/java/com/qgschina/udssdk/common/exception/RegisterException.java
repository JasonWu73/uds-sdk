package com.qgschina.udssdk.common.exception;

/**
 * 服务注册错误
 */
public class RegisterException extends UdsSdkException {

  public RegisterException() {
  }

  public RegisterException(String message) {
    super(message);
  }

  public RegisterException(String message, Throwable cause) {
    super(message, cause);
  }
}
