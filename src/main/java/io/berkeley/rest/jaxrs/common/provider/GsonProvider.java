package io.berkeley.rest.jaxrs.common.provider;


import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;


@SuppressWarnings("UnusedDeclaration")
@Provider
@Consumes({MediaType.APPLICATION_JSON, "text/json"})
@Produces({MediaType.APPLICATION_JSON, "text/json"})
public class GsonProvider
        implements MessageBodyReader<Object>, MessageBodyWriter<Object>, InitializingBean {

    //------------------------------------------------------------------------------------------------
    // Variables - Private - Static
    //------------------------------------------------------------------------------------------------

    private static final Logger LOGGER = LoggerFactory.getLogger(GsonProvider.class);


    //-------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------

    private final static ImmutableSet<Class<?>> CLASSES_TO_NOT_SERIALIZE = ImmutableSet.<Class<?>>builder()
            .add(java.io.InputStream.class)
            .add(java.io.Reader.class)
            .add(java.io.OutputStream.class)
            .add(java.io.Writer.class)
            .add(char[].class)
            .add(StreamingOutput.class)
            .add(Response.class).build();

    private static final String UTF_8_CHARSET = "charset=" + Charsets.UTF_8;


    //-------------------------------------------------------------
    // Variables - Private
    //-------------------------------------------------------------

    private boolean serializeNulls = true;
    private boolean disableHtmlEscaping = true;
    private String dateFormatPattern = null;
    private FieldNamingPolicy fieldNamingPolicy = FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
    private Gson gson = new GsonBuilder().setFieldNamingPolicy(fieldNamingPolicy).serializeNulls().disableHtmlEscaping().setDateFormat(dateFormatPattern).create();
    private Map<Object, Type> typeAdapters = Collections.emptyMap();
    private List<ExclusionStrategy> exclusionStrategies = Collections.emptyList();


    //-------------------------------------------------------------
    // Implementation - InitializingBean
    //-------------------------------------------------------------

    @Override
    public void afterPropertiesSet() throws Exception {
        final GsonBuilder builder = new GsonBuilder().setFieldNamingPolicy(fieldNamingPolicy);
        if (serializeNulls) {
            builder.serializeNulls();
        }
        if (disableHtmlEscaping) {
            builder.disableHtmlEscaping();
        }
        if (!typeAdapters.isEmpty()) {
            for (Map.Entry<Object, Type> entry : typeAdapters.entrySet()) {
                LOGGER.debug("Registering type {} to adapter {}.", entry.getValue(), entry.getKey());
                builder.registerTypeAdapter(entry.getValue(), entry.getKey());
            }
        }
        if (!exclusionStrategies.isEmpty()) {
            builder.setExclusionStrategies(exclusionStrategies.toArray(new ExclusionStrategy[exclusionStrategies.size()]));
        }
        if (!Strings.isNullOrEmpty(dateFormatPattern)) {
            builder.setDateFormat(dateFormatPattern);
        }
        gson = builder.create();
    }


    //-------------------------------------------------------------
    // Implementations - MessageBodyWriter
    //-------------------------------------------------------------

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return isReadWritable(type, mediaType);
    }


    @Override
    public long getSize(Object t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }


    @Override
    public void writeTo(Object t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                        MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException {
        if (httpHeaders != null) {
            httpHeaders.putSingle(javax.ws.rs.core.HttpHeaders.CONTENT_TYPE, mediaType.toString() + ";" + UTF_8_CHARSET);
        }

        IOUtils.write(gson.toJson(t, type), entityStream, Charsets.UTF_8);
        entityStream.flush();
    }


    //-------------------------------------------------------------
    // Implementations - MessageBodyReader
    //-------------------------------------------------------------

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return isReadWritable(type, mediaType);
    }


    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                           MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException, WebApplicationException {
        return gson.fromJson(IOUtils.toString(entityStream, Charsets.UTF_8), type);
    }


    //-------------------------------------------------------------
    // Methods - Setters
    //-------------------------------------------------------------

    public void setSerializeNulls(boolean serializeNulls) {
        this.serializeNulls = serializeNulls;
    }


    public void setDisableHtmlEscaping(boolean disableHtmlEscaping) {
        this.disableHtmlEscaping = disableHtmlEscaping;
    }


    public void setDateFormatPattern(String dateFormatPattern) {
        this.dateFormatPattern = dateFormatPattern;
    }


    public void setFieldNamingPolicy(FieldNamingPolicy fieldNamingPolicy) {
        this.fieldNamingPolicy = fieldNamingPolicy;
    }


    public void setTypeAdapters(Map<Object, Type> typeAdapters) {
        this.typeAdapters = typeAdapters;
    }


    public void setExclusionStrategies(List<ExclusionStrategy> exclusionStrategies) {
        this.exclusionStrategies = exclusionStrategies;
    }


    //-------------------------------------------------------------
    // Methods - Getters
    //-------------------------------------------------------------

    public final Gson getGson() {
        return gson;
    }


    //-------------------------------------------------------------
    // Methods - Private
    //-------------------------------------------------------------

    private boolean isJsonType(MediaType mediaType) {
        if (mediaType != null) {
            String subtype = mediaType.getSubtype();
            return "json".equalsIgnoreCase(subtype) || subtype.endsWith("+json");
        }

        return true;
    }


    private boolean isReadWritable(Class<?> type, MediaType mediaType) {
        if (!isJsonType(mediaType)) {
            return false;
        }

        if (CLASSES_TO_NOT_SERIALIZE.contains(type)) {
            return false;
        }

        for (Class<?> classToNotSerialize : CLASSES_TO_NOT_SERIALIZE) {
            if (classToNotSerialize.isAssignableFrom(type)) {
                return false;
            }
        }

        return true;
    }
}
