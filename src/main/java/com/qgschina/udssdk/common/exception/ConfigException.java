package com.qgschina.udssdk.common.exception;

/**
 * 配置错误
 */
public class ConfigException extends UdsSdkException {

  public ConfigException() {
  }

  public ConfigException(String message) {
    super(message);
  }

  public ConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
