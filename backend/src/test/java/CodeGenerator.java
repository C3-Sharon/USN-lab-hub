import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;

public class CodeGenerator {
    public static void main(String[] args) {
        String projectPath = System.getProperty("user.dir");
        String dbUrl = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/usn_hub");
        String dbUsername = System.getenv().getOrDefault("DB_USERNAME", "root");
        String dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "");

        FastAutoGenerator.create(dbUrl, dbUsername, dbPassword)
                .globalConfig(builder -> {
                    builder.author("USN Lab Hub")
                            .outputDir(projectPath + "/backend/src/main/java")
                            .fileOverride();
                })
                .packageConfig(builder -> {
                    builder.parent("com.usn.labhub")
                            .moduleName("user");
                })
                .strategyConfig(builder -> {
                    builder.addInclude(
                                    "sys_user",
                                    "sys_role",
                                    "sys_identity",
                                    "sys_group",
                                    "sys_faculty_major",
                                    "sys_user_role",
                                    "attendance_record"
                            )
                            .entityBuilder()
                            .enableLombok()
                            .enableTableFieldAnnotation()
                            .idType(IdType.AUTO);
                })
                .execute();
    }
}
