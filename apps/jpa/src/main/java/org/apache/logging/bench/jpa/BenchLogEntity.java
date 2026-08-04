package org.apache.logging.bench.jpa;

import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.db.jpa.BasicLogEventEntity;
import org.apache.logging.log4j.core.appender.db.jpa.converter.ContextMapJsonAttributeConverter;
import org.apache.logging.log4j.core.appender.db.jpa.converter.ContextStackJsonAttributeConverter;

/**
 * The entity the JPA appender persists. Feature matrix §1.
 *
 * <p>Unlike every other appender, JPA needs the application to supply a class:
 * {@code BasicLogEventEntity} is a {@code @MappedSuperclass} that maps the
 * standard event fields, and this subclass adds the two things it cannot know —
 * an {@code @Entity}/{@code @Table} to persist into, and a mutable {@code @Id}.
 *
 * <p>Three requirements that produce obscure failures when missed:
 * <ul>
 *   <li>A public no-arg constructor AND a {@code LogEvent} one. JPA needs the
 *       first to materialise rows; the appender calls the second for every
 *       event. Omit the second and the appender fails at write time with a
 *       NoSuchMethodException naming a constructor you never wrote.</li>
 *   <li>Annotations on the GETTER, not the field. {@code BasicLogEventEntity}
 *       annotates its accessors, and JPA requires one consistent access type
 *       per hierarchy — mixing them means the superclass mappings are ignored
 *       and the row comes out mostly null.</li>
 *   <li>Converters for the structured columns. A {@code Map} or a stack has no
 *       default JPA mapping, so without {@code @Convert} the provider either
 *       refuses the entity or silently serialises something unusable.</li>
 * </ul>
 */
@Entity
@Table(name = "BENCH_LOG_EVENTS")
public class BenchLogEntity extends BasicLogEventEntity {

    private static final long serialVersionUID = 1L;

    private long id;

    /**
     * Required by JPA to materialise a row.
     *
     * <p>It must call the protected no-arg super constructor, NOT
     * {@code super(null)}. The wrapper constructor rejects a null event, and
     * because JPA only ever invokes this reflectively the failure arrives as
     * "Problem in creating new instance using the default constructor. The
     * default constructor triggered an exception." with an
     * InvocationTargetException and no mention of this class.
     */
    public BenchLogEntity() {
        super();
    }

    /** Required by the appender: it constructs one of these per event. */
    public BenchLogEntity(final LogEvent wrapped) {
        super(wrapped);
    }

    /**
     * SEQUENCE, not IDENTITY.
     *
     * <p>EclipseLink 2.7's H2 platform fetches an IDENTITY key with
     * {@code CALL IDENTITY()}, a function H2 2.x removed — so every insert
     * fails with "Function IDENTITY not found" even once the table exists.
     * EclipseLink 2.7 is the last release on javax.persistence and predates
     * H2 2.x, so this is not a version anyone can upgrade out of while staying
     * on javax. A sequence avoids the dialect-specific call entirely.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "benchSeq")
    @javax.persistence.SequenceGenerator(name = "benchSeq", sequenceName = "BENCH_LOG_SEQ",
                                         allocationSize = 50)
    @Column(name = "ID")
    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    /** The MDC, as JSON in one column. */
    @Override
    @Convert(converter = ContextMapJsonAttributeConverter.class)
    @Column(name = "CONTEXT_MAP")
    public Map<String, String> getContextMap() {
        return super.getContextMap();
    }

    /** The NDC, likewise. */
    @Override
    @Convert(converter = ContextStackJsonAttributeConverter.class)
    @Column(name = "CONTEXT_STACK")
    public org.apache.logging.log4j.ThreadContext.ContextStack getContextStack() {
        return super.getContextStack();
    }
}
