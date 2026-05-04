package com.techcorp.assistant.module06.dokimos;

import dev.dokimos.core.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for loading evaluation datasets in Dokimos format.
 * Handles JSON parsing and error handling for missing/malformed files.
 */
@Service
public class DatasetLoader {

    private static final Logger log = LoggerFactory.getLogger(DatasetLoader.class);

    @Value("${evaluation.dataset.path}")
    private String datasetPath;

    /**
     * Loads the evaluation dataset from the configured path.
     *
     * @return Dokimos Dataset object with examples
     * @throws DatasetLoadException if dataset file is missing or malformed
     */
    public Dataset loadDataset() throws DatasetLoadException {
        Path path = Paths.get(datasetPath);

        if (!path.toFile().exists()) {
            String errorMsg = String.format("Dataset file not found at path: %s", datasetPath);
            log.error(errorMsg);
            throw new DatasetLoadException(errorMsg);
        }

        if (!path.toFile().canRead()) {
            String errorMsg = String.format("Dataset file is not readable: %s", datasetPath);
            log.error(errorMsg);
            throw new DatasetLoadException(errorMsg);
        }

        try {
            log.info("Loading dataset from: {}", datasetPath);
            Dataset dataset = Dataset.fromJson(path);
            log.info("Successfully loaded dataset '{}' with {} examples",
                    dataset.name(),
                    dataset.examples().size());
            return dataset;

        } catch (IOException e) {
            String errorMsg = String.format("Failed to read dataset file: %s", datasetPath);
            log.error(errorMsg, e);
            throw new DatasetLoadException(errorMsg, e);

        } catch (Exception e) {
            String errorMsg = String.format("Malformed dataset JSON in file: %s", datasetPath);
            log.error(errorMsg, e);
            throw new DatasetLoadException(errorMsg, e);
        }
    }

    /**
     * Exception thrown when dataset loading fails.
     */
    public static class DatasetLoadException extends Exception {
        public DatasetLoadException(String message) {
            super(message);
        }

        public DatasetLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
