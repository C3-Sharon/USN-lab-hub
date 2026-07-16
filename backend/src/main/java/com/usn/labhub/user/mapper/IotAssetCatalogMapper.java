package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.iot.IotDeviceRecord;
import com.usn.labhub.user.domain.entity.iot.LabProjectRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IotAssetCatalogMapper {

    @Select("""
            SELECT id, project_code, project_name, description, status, public_visible
            FROM lab_project
            WHERE project_code=#{projectCode} AND public_visible=1
            """)
    LabProjectRecord selectPublicProject(@Param("projectCode") String projectCode);

    @Select("""
            SELECT id, project_id, device_code, device_name
            FROM iot_device
            WHERE project_id=#{projectId} AND device_code=#{deviceCode}
            """)
    IotDeviceRecord selectDevice(@Param("projectId") Long projectId,
                                 @Param("deviceCode") String deviceCode);

    @Select("SELECT COUNT(*) FROM iot_device WHERE project_id=#{projectId}")
    int countDevices(@Param("projectId") Long projectId);
}
