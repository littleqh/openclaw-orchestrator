package com.openclaw.orchestrator;

import com.openclaw.orchestrator.entity.Skill;
import com.openclaw.orchestrator.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final SkillRepository skillRepository;

    @Override
    public void run(String... args) {
        if (skillRepository.count() == 0) {
            log.info("Initializing preset skills...");
            List<Skill> skills = List.of(
                Skill.builder().name("代码编写").description("编写和调试代码").build(),
                Skill.builder().name("文档生成").description("生成各类文档").build(),
                Skill.builder().name("数据分析").description("分析和可视化数据").build(),
                Skill.builder().name("任务规划").description("制定执行计划").build(),
                Skill.builder().name("信息检索").description("搜索和整理信息").build(),
                Skill.builder().name("对话沟通").description("自然语言对话").build(),
                Skill.builder().name("图像识别").description("识别和处理图像").build(),
                Skill.builder().name("翻译").description("多语言翻译").build()
            );
            skillRepository.saveAll(skills);
            log.info("Preset skills initialized: {} skills", skills.size());
        }
    }
}
