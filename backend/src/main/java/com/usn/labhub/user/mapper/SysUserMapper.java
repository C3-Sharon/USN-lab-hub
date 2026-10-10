package com.usn.labhub.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usn.labhub.user.common.auth.LoginAccount;
import com.usn.labhub.user.domain.dto.MemberQueryDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.vo.MemberVO;
import com.usn.labhub.user.domain.vo.RoleInfoVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    LoginAccount selectLoginUser(@Param("memberId") String memberId);

    List<RoleInfoVO> selectRolesByUserId(@Param("userId") Long userId);

    @Select("SELECT id FROM sys_role WHERE role_key=#{roleKey}")
    Integer selectRoleIdByKey(@Param("roleKey") String roleKey);

    IPage<MemberVO> selectMemberPage(Page<MemberVO> page, @Param("query") MemberQueryDTO query);

    @Insert("INSERT IGNORE INTO sys_user_role(user_id, role_id) VALUES(#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Integer roleId);
}
