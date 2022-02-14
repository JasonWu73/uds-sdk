package com.qgschina.udssdk.common.exception;

/**
 * 服务 Socket 文件被占用
 */
public class NamespaceOccupiedException extends UdsSdkException {

  public NamespaceOccupiedException() {
  }

  public NamespaceOccupiedException(String message) {
    super(message);
  }

  public NamespaceOccupiedException(String message, Throwable cause) {
    super(message, cause);
  }
}
