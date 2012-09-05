package com.mobeelizer.java;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mobeelizer.java.api.MobeelizerErrorsBuilder;
import com.mobeelizer.java.api.MobeelizerFile;
import com.mobeelizer.java.api.MobeelizerModel;
import com.mobeelizer.java.api.MobeelizerOperationError;
import com.mobeelizer.java.api.MobeelizerOperationStatus;
import com.mobeelizer.java.connection.MobeelizerConnectionService;
import com.mobeelizer.java.model.MobeelizerModelImpl;
import com.mobeelizer.java.sync.MobeelizerInputData;
import com.mobeelizer.java.sync.MobeelizerJsonEntity;
import com.mobeelizer.java.sync.MobeelizerJsonEntity.ConflictState;
import com.mobeelizer.java.sync.MobeelizerOutputData;

public class MobeelizerSyncService {

    private static final Logger logger = LoggerFactory.getLogger(MobeelizerSyncService.class);

    private final MobeelizerConnectionService connectionService;

    private final Map<Class<?>, MobeelizerModelImpl> definitionByClass = new HashMap<Class<?>, MobeelizerModelImpl>();

    private final Map<String, MobeelizerModelImpl> definitionByName = new HashMap<String, MobeelizerModelImpl>();

    private boolean hasDefinition = false;

    public MobeelizerSyncService(final Set<MobeelizerModel> definition, final MobeelizerConnectionService connectionService) {
        if (definition != null) {
            hasDefinition = true;
            for (MobeelizerModel model : definition) {
                definitionByClass.put(model.getMappingClass(), (MobeelizerModelImpl) model);
                definitionByName.put(model.getName(), (MobeelizerModelImpl) model);
            }
        }
        this.connectionService = connectionService;
    }

    public void syncAll(final MobeelizerSyncCallback callback) {
        sync(null, null, callback, true);
    }

    public void sync(final Iterable<Object> entities, final Iterable<MobeelizerFile> files, final MobeelizerSyncCallback callback) {
        sync(entities, files, callback, false);
    }

    private void sync(final Iterable<Object> outputEntities, final Iterable<MobeelizerFile> outputFiles,
            final MobeelizerSyncCallback callback, final boolean isAllSynchronization) {
        final MobeelizerInputData inputData;

        File outputFile = null;
        File inputFile = null;

        try {
            final String ticket;

            if (isAllSynchronization) {
                MobeelizerOperationStatus<String> syncResult = connectionService.sendSyncAllRequest();
                if (syncResult.getError() != null) {
                    callback.onSyncFinishedWithError(syncResult.getError());
                    return;
                }
                ticket = syncResult.getContent();
            } else {
                outputFile = File.createTempFile("mobeelizer", "sync");

                MobeelizerErrorsBuilder errorsBuilder = new MobeelizerErrorsBuilder();

                prepareOutputFile(outputFile, outputEntities, outputFiles, errorsBuilder);

                if (!errorsBuilder.hasNoErrors()) {
                    callback.onSyncFinishedWithError(errorsBuilder.createWhenErrors());
                    return;
                }
                MobeelizerOperationStatus<String> syncResult = connectionService.sendSyncDiffRequest(outputFile);
                if (syncResult.getError() != null) {
                    callback.onSyncFinishedWithError(syncResult.getError());
                    return;
                }
                ticket = syncResult.getContent();
            }

            MobeelizerOperationError waitResult = connectionService.waitUntilSyncRequestComplete(ticket);
            if (waitResult != null) {
                callback.onSyncFinishedWithError(waitResult);
                return;
            }

            inputFile = connectionService.getSyncData(ticket);

            inputData = new MobeelizerInputData(new FileInputStream(inputFile), File.createTempFile("mobeelizer", "input"));

            callback.onSyncFinishedWithSuccess(prepareInputEntitiesIterator(inputData), prepareInputFileIterator(inputData),
                    inputData.getDeletedFiles(), new MobeelizerConfirmSyncCallback() {

                        @Override
                        public void confirm() {
                            try {
                                MobeelizerOperationError confirmResult = connectionService.confirmTask(ticket);
                                if (confirmResult != null) {
                                    logger.warn("Cannot confirm task: " + confirmResult.getMessage());
                                    callback.onSyncFinishedWithError(confirmResult);
                                    return;
                                }
                            } finally {
                                if (inputData != null) {
                                    inputData.close();
                                }
                            }
                        }

                    });
        } catch (Exception e) {
            callback.onSyncFinishedWithError(MobeelizerOperationError.other(e));
        } finally {
            if (outputFile != null && !outputFile.delete()) {
                logger.warn("Cannot delete file " + outputFile.getAbsolutePath());
            }
            if (inputFile != null && !inputFile.delete()) {
                logger.warn("Cannot delete file " + inputFile.getAbsolutePath());
            }
        }
    }

    private Iterable<MobeelizerFile> prepareInputFileIterator(final MobeelizerInputData inputData) throws IOException {
        List<MobeelizerFile> files = new ArrayList<MobeelizerFile>();

        for (final String file : inputData.getFiles()) {
            InputStream input = inputData.getFile(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = input.read(buffer)) > -1) {
                baos.write(buffer, 0, len);
            }
            baos.flush();
            final InputStream copyInput = new ByteArrayInputStream(baos.toByteArray());

            files.add(new MobeelizerFile() {

                @Override
                public String getName() {
                    return null;
                }

                @Override
                public InputStream getInputStream() {
                    return copyInput;
                }

                @Override
                public String getGuid() {
                    return file;
                }

                @Override
                public File getFile() {
                    throw new UnsupportedOperationException();
                }

            });
        }

        return files;
    }

    private MobeelizerModelImpl getModel(final Class<?> clazz) {
        if (!definitionByClass.containsKey(clazz)) {
            throw new IllegalStateException("Class " + clazz.getCanonicalName() + " not mapped.");
        }
        return definitionByClass.get(clazz);
    }

    private MobeelizerModelImpl getModel(final String name) {
        if (!definitionByName.containsKey(name)) {
            throw new IllegalStateException("Class for model " + name + " not mapped.");
        }
        return definitionByName.get(name);
    }

    private Iterable<Object> prepareInputEntitiesIterator(final MobeelizerInputData inputData) {

        List<Object> entities = new ArrayList<Object>();

        for (MobeelizerJsonEntity entity : inputData.getInputData()) {
            if (hasDefinition) {
                entities.add(getModel(entity.getModel()).getEntityFromJsonEntity(entity));
            } else {
                Map<String, String> entityMap = new HashMap<String, String>(entity.getFields());
                entityMap.put("model", entity.getModel());
                entityMap.put("guid", entity.getGuid());
                entityMap.put("owner", entity.getOwner());
                entityMap.put("conflicted", (entity.getConflictState() != ConflictState.NO_IN_CONFLICT) + "");
                entities.add(entityMap);
            }
        }
        return entities;
    }

    private void prepareOutputFile(final File outputFile, final Iterable<Object> entities, final Iterable<MobeelizerFile> files,
            final MobeelizerErrorsBuilder errors) {
        MobeelizerOutputData outputData = null;

        try {
            outputData = new MobeelizerOutputData(outputFile, File.createTempFile("mobeelizer", "output"));

            if (entities != null) {
                for (Object entity : entities) {
                    if (hasDefinition) {
                        outputData.writeEntity(getModel(entity.getClass()).getJsonEntityFromEntity(entity, errors));
                    } else {
                        @SuppressWarnings("unchecked")
                        Map<String, String> entityMap = new HashMap<String, String>((Map<String, String>) entity);
                        MobeelizerJsonEntity jsonEntity = new MobeelizerJsonEntity();
                        jsonEntity.setModel(entityMap.get("model"));
                        jsonEntity.setGuid(entityMap.get("guid"));
                        jsonEntity.setOwner(entityMap.get("owner"));
                        entityMap.remove("model");
                        entityMap.remove("guid");
                        entityMap.remove("owner");
                        jsonEntity.setFields(entityMap);
                        outputData.writeEntity(jsonEntity);
                    }
                }
            }

            if (files != null) {
                for (MobeelizerFile file : files) {
                    outputData.writeFile(file.getGuid(), file.getInputStream());
                }
            }

        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } finally {
            if (outputData != null) {
                outputData.close();
            }
        }
    }
}
