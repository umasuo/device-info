package com.umasuo.device.center.application.service;

import com.google.common.collect.Lists;
import com.umasuo.device.center.application.dto.ProductView;
import com.umasuo.device.center.application.dto.UnionDeviceRequest;
import com.umasuo.device.center.application.dto.UnionDeviceView;
import com.umasuo.device.center.application.dto.UnionRegisterRequest;
import com.umasuo.device.center.application.dto.mapper.UnionMapper;
import com.umasuo.device.center.domain.model.UnionDevice;
import com.umasuo.device.center.domain.service.UnionDeviceService;
import com.umasuo.device.center.infrastructure.enums.ProductStatus;
import com.umasuo.exception.AlreadyExistException;
import com.umasuo.exception.AuthFailedException;
import com.umasuo.exception.CreateResourceFailed;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Created by Davis on 17/7/19.
 */
@Service
public class UnionApplication {

  /**
   * Logger.
   */
  private static final Logger LOG = LoggerFactory.getLogger(UnionApplication.class);

  /**
   * Token length.
   */
  private static final int SECRET_KEY_LENGTH = 7;

  @Autowired
  public transient UnionDeviceService unionDeviceService;

  @Autowired
  private transient RestClient restClient;

  /**
   * 批量新建union id.
   *
   * @param developerId the developer id
   * @param request the quantity
   * @return the list
   */
  public List<UnionDeviceView> batchCreate(String developerId, UnionDeviceRequest request) {
    LOG.debug("Enter. developerId: {}, request: {}.", developerId, request);

    List<UnionDevice> unionDevices = Lists.newArrayList();

    checkProduct(developerId, request.getProductId());

    // todo 这将是一个漫长的请求, 优化方法：预先生成一定数量的uuid和secret key
    for (int i = 0; i < request.getQuantity(); i++) {
      UnionDevice unionDevice = new UnionDevice();
      unionDevice.setDeveloperId(developerId);
      unionDevice.setProductId(request.getProductId());
      unionDevice.setUnionId(UUID.randomUUID().toString());
      // set a random secret key
      unionDevice.setSecretKey(RandomStringUtils.randomAlphanumeric(SECRET_KEY_LENGTH));
      unionDevices.add(unionDevice);
    }

    unionDeviceService.save(unionDevices);

    List<UnionDeviceView> result = UnionMapper.toView(unionDevices);

    LOG.debug("Exit.");

    return result;
  }

  public UnionDeviceView register(String developerId, UnionRegisterRequest request) {
    LOG.debug("Enter. developerId: {}, request: {}.", developerId, request);
    UnionDevice unionDevice = new UnionDevice();
    unionDevice.setDeveloperId(developerId);

    checkProduct(developerId, request.getProductId());

    unionDevice.setProductId(request.getProductId());
    unionDevice.setSecretKey(RandomStringUtils.randomAlphanumeric(SECRET_KEY_LENGTH));
    String unionId = UUID.randomUUID().toString();
    unionDevice.setUnionId(unionId);

    if (unionDeviceService.isUnionIdExist(unionId)) {
      LOG.debug("unionId: {} is already exist.", unionId);
      throw new AlreadyExistException("UnionId is already exist");
    }

    unionDeviceService.save(unionDevice);

    LOG.debug("Exit. unionDevice: {}.", unionDevice);
    return UnionMapper.toView(unionDevice);
  }

  private void checkProduct(String developerId, String productId) {
    ProductView product = restClient.getProduct(developerId, productId);

    if (developerId.equals(product.getDeveloperId())) {
      LOG.debug("Developer: {} don't own this product: {}.", developerId, productId);
      throw new AuthFailedException("Developer do not own this product");
    }

    if (!ProductStatus.PUBLISHED.equals(product.getStatus())) {
      LOG.debug("Product: {} is not published.", productId);
      throw new CreateResourceFailed("Product is not published");
    }
  }
}
