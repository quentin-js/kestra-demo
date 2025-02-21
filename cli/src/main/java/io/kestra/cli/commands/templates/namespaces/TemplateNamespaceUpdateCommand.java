package io.kestra.cli.commands.templates.namespaces;

import io.kestra.cli.commands.AbstractServiceNamespaceUpdateCommand;
import io.kestra.cli.commands.templates.TemplateValidateCommand;
import io.kestra.core.models.templates.Template;
import io.kestra.core.serializers.YamlFlowParser;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import javax.validation.ConstraintViolationException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

@CommandLine.Command(
    name = "update",
    description = "handle namespace templates",
    mixinStandardHelpOptions = true
)
@Slf4j
public class TemplateNamespaceUpdateCommand extends AbstractServiceNamespaceUpdateCommand {
    @Inject
    public YamlFlowParser yamlFlowParser;

    @Override
    public Integer call() throws Exception {
        super.call();

        try {
            List<Template> templates = Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(YamlFlowParser::isValidExtension)
                .map(path -> yamlFlowParser.parse(path.toFile(), Template.class))
                .collect(Collectors.toList());

            if (templates.size() == 0) {
                stdOut("No template found on '{}'", directory.toFile().getAbsolutePath());
            }

            try (DefaultHttpClient client = client()) {
                MutableHttpRequest<List<Template>> request = HttpRequest
                    .POST("/api/v1/templates/" + namespace + "?delete=" + !noDelete, templates);

                List<Template> updated = client.toBlocking().retrieve(
                    this.requestOptions(request),
                    Argument.listOf(Template.class)
                );

                stdOut(updated.size() + " template(s) for namespace '" + namespace + "' successfully updated !");
                updated.forEach(template -> stdOut("- " + template.getNamespace() + "." + template.getId()));
            } catch (HttpClientResponseException e) {
                TemplateValidateCommand.handleHttpException(e, "template");

                return 1;
            }
        } catch (ConstraintViolationException e) {
            TemplateValidateCommand.handleException(e, "template");

            return 1;
        }

        return 0;
    }
}
