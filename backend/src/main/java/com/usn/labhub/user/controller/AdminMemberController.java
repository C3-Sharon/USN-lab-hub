package com.usn.labhub.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.MemberQueryDTO;
import com.usn.labhub.user.domain.dto.MemberSaveDTO;
import com.usn.labhub.user.domain.dto.MemberUpdateDTO;
import com.usn.labhub.user.domain.vo.MemberVO;
import com.usn.labhub.user.service.ISysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/member")
@Tag(name = "实验室成员管理", description = "成员增删改查与分页多条件检索")
public class AdminMemberController {

    @Autowired
    private ISysUserService userService;

    @PostMapping("/page")
    @Operation(summary = "分页多条件查询成员")
    public Result<IPage<MemberVO>> page(@Valid @RequestBody MemberQueryDTO queryDTO) {
        return Result.success(userService.pageMembers(queryDTO));
    }

    @GetMapping("/page")
    @Operation(summary = "分页多条件查询成员")
    public Result<IPage<MemberVO>> pageByQuery(@Valid MemberQueryDTO queryDTO) {
        return Result.success(userService.pageMembers(queryDTO));
    }

    @PostMapping("/save")
    @Operation(summary = "新增成员")
    public Result<Void> save(@Valid @RequestBody MemberSaveDTO saveDTO) {
        userService.saveMember(saveDTO);
        return Result.success(null);
    }

    @PostMapping("/update")
    @Operation(summary = "更新成员")
    public Result<Void> update(@Valid @RequestBody MemberUpdateDTO updateDTO) {
        userService.updateMember(updateDTO);
        return Result.success(null);
    }

    @PostMapping("/status/{id}/{status}")
    @Operation(summary = "切换成员状态")
    public Result<Void> updateStatus(@PathVariable @NotNull Long id, @PathVariable @NotNull Byte status) {
        userService.updateMemberStatus(id, status);
        return Result.success(null);
    }

    @PutMapping("/status/{id}/{status}")
    @Operation(summary = "切换成员状态")
    public Result<Void> putStatus(@PathVariable @NotNull Long id, @PathVariable @NotNull Byte status) {
        userService.updateMemberStatus(id, status);
        return Result.success(null);
    }
}
