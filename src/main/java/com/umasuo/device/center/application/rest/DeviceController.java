package com.umasuo.device.center.application.rest;

import com.umasuo.device.center.application.dto.DeviceDraft;
import com.umasuo.device.center.application.dto.DeviceView;
import com.umasuo.device.center.application.service.DeviceApplication;
import com.umasuo.device.center.infrastructure.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import javax.validation.Valid;

/**
 * Created by umasuo on 17/6/5.
 */
@RestController
@CrossOrigin
public class DeviceController {

  private final static Logger logger = LoggerFactory.getLogger(DeviceController.class);

  @Autowired
  private transient DeviceApplication deviceApplication;


  @PostMapping(Router.DEVICE_CENTER_ROOT)
  public DeviceView addDevice(@RequestBody @Valid DeviceDraft draft) {
    logger.info("Enter. deviceDraft: {}.", draft);

    DeviceView view = deviceApplication.addDevice(draft);

    logger.info("Exit. deviceView: {}.", view);
    return view;
  }

  /**
   * get device by device id.
   *
   * @param id String device id
   * @return DeviceView
   */
  public DeviceView getDevice(@PathVariable String id) {
    logger.info("Enter. deviceId: {}.", id);

    DeviceView view = deviceApplication.getByDeviceId(id);

    logger.info("Exit. deviceView: {}.", view);
    return view;
  }

  /**
   * 获取用户在某个开啊这下的所有设备.
   *
   * @param userId      String
   * @param developerId String in header
   * @return list of device view
   */
  public List<DeviceView> getAllDeviceByUser(@RequestParam String userId,
                                             @RequestHeader String developerId) {
    logger.info("Enter. userId: {}, developerId: {}.", userId, developerId);

    List<DeviceView> views = deviceApplication.getByUserAndDeveloper(userId, developerId);

    logger.info("Exit. views: {}.", views);
    return views;
  }
}