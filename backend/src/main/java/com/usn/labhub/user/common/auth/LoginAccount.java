package com.usn.labhub.user.common.auth;

import lombok.Data;

@Data
public class LoginAccount {
    private Long id;
    private String username;
    private String password;
    private String memberId;
    private Byte status;
    private String identityName;
    private String groupName;
    private String collegeName;
    private String majorName;
}
