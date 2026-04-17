import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;

public class CodeGenerator {
    public static void main(String[] args) {
        String projectPath = System.getProperty("user.dir");
        FastAutoGenerator.create("jdbc:mysql://localhost:3306/usn_hub", "root", "523123")
                .globalConfig(builder -> {
                    builder.author("陈思瑞") // 设置作者
                            .outputDir(projectPath + "/backend/src/main/java") // 拼成绝对路径
                            .fileOverride(); // 加上这个，解决你日志里那些“文件已存在”的警告
                })
                .packageConfig(builder -> {
                    builder.parent("com.usn.labhub") // 设置父包名
                            .moduleName("user"); // 设置模块名
                })
                .strategyConfig(builder -> {
                    builder.addInclude(
                                    "sys_user",             // 用户主表
                                    "sys_role",             // 角色表
                                    "sys_identity",         // 身份表
                                    "sys_group",            // 工作组表
                                    "sys_faculty_major",    // 学院专业表
                                    "sys_user_role",        // 用户角色关联表
                                    "attendance_record"     // 考勤记录表
                            )
                            .entityBuilder()
                            .enableLombok()             // 必须开启，省去手写 getter/setter
                            .enableTableFieldAnnotation()
                            .idType(IdType.AUTO);       // 对应我们 SQL 里的自增主键
                })
                .execute();
    }
}
