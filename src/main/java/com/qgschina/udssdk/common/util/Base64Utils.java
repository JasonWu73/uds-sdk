package com.qgschina.udssdk.common.util;

import java.util.Base64;

/**
 * Base64 工具类
 */
public class Base64Utils {

  /**
   * 执行 Base64 解码
   *
   * <ul>
   *   <li>Basic type base64</li>
   *   <li>MIME type</li>
   *   <li>URL and Filename safe type</li>
   * </ul>
   *
   * @param src 需要 Base64 解析的源字符串
   * @return 通过 Base64 解码后得到的字节数组
   */
  public static byte[] decode(String src) {
    byte[] bytes;

    try {
      bytes = Base64.getMimeDecoder().decode(src);
    } catch (IllegalArgumentException e) {
      try {
        bytes = Base64.getUrlDecoder().decode(src);
      } catch (IllegalArgumentException e1) {
        try {
          bytes = Base64.getDecoder().decode(src);
        } catch (IllegalArgumentException e2) {
          throw new IllegalArgumentException("存在非法 Base64 字符");
        }
      }
    }

    return bytes;
  }
}
