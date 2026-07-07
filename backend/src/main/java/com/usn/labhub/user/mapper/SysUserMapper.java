package com.usn.labhub.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usn.labhub.user.domain.dto.MemberQueryDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.vo.MemberVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    Map<String, Object> selectRole(@Param("memberId") String memberId);

    IPage<MemberVO> selectMemberPage(Page<MemberVO> page, @Param("query") MemberQueryDTO query);

    @Insert("INSERT IGNORE INTO sys_user_role(user_id, role_id) VALUES(#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Integer roleId);
}
