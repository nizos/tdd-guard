package io.github.nizos.tddguard.junit5;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TddGuardPlugin implements Plugin<Project> {

    static final Set<String> COMPILATION_TASKS = Set.of("compileJava", "compileTestJava");

    @Override
    public void apply(Project project) {
        String projectRoot = project.getProviders()
                .environmentVariable(ProjectRootResolver.ENV_VAR)
                .getOrNull();
        if (projectRoot == null) {
            return;
        }

        project.getTasks().withType(JavaCompile.class, task -> {
            if (!COMPILATION_TASKS.contains(task.getName())) {
                return;
            }

            List<String> capturedLines = new ArrayList<>();
            StandardOutputListener listener = line -> capturedLines.add(line.toString());

            task.doFirst(t -> task.getLogging().addStandardErrorListener(listener));

            project.getGradle().getTaskGraph().whenReady(graph -> graph.afterTask(t -> {
                if (!t.equals(task) || t.getState().getFailure() == null) {
                    return;
                }

                task.getLogging().removeStandardErrorListener(listener);

                Path outputDir = Path.of(projectRoot).resolve(TddGuardListener.DATA_SUBPATH);
                String capturedOutput = String.join("\n", capturedLines);
                Set<File> sourceFiles = ((JavaCompile) task).getSource().getFiles();
                CompilationErrorHandler.handle(outputDir, capturedOutput, sourceFiles);
            }));
        });
    }
}
