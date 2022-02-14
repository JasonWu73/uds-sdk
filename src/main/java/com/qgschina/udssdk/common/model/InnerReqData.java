package com.qgschina.udssdk.common.model;

import com.qgschina.udssdk.common.constant.InnerParamType;
import com.qgschina.udssdk.common.constant.InnerReqType;
import java.util.List;
import lombok.Data;

/**
 * 用于 SDK 内部的 Client 请求参数
 */
@Data
public class InnerReqData {

  /**
   * 请求类型, 详见 {@link InnerReqType#value()}
   */
  private String type;

  /**
   * 信号名（仅信号触发或订阅时才存在）
   */
  private String signal;

  /**
   * 方法名（仅方法调用时才存在）
   */
  private String method;

  /**
   * 参数数据列表
   */
  private List<Object> data;

  /**
   * 参数类型统一标识列表, 详见 {@link InnerParamType#value()}
   */
  private List<String> parameterTypes;
}
