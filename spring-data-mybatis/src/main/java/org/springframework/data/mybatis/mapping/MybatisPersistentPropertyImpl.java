/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mybatis.mapping;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderColumn;
import javax.persistence.Transient;
import javax.persistence.Version;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.mybatis.annotation.Snowflake;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.Lazy;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;

/**
 * {@link MybatisPersistentProperty} implementation.
 *
 * @author JARVIS SONG
 */
class MybatisPersistentPropertyImpl extends AnnotationBasedPersistentProperty<MybatisPersistentProperty>
		implements MybatisPersistentProperty {

	private static Collection<Class<? extends Annotation>> ENTITY_ANNOTATIONS;

	private static final Collection<Class<? extends Annotation>> ASSOCIATION_ANNOTATIONS;

	private static final Collection<Class<? extends Annotation>> ID_ANNOTATIONS;

	private static final Collection<Class<? extends Annotation>> UPDATEABLE_ANNOTATIONS;

	static {

		Set<Class<? extends Annotation>> annotations = new HashSet<>();
		annotations.add(OneToMany.class);
		annotations.add(OneToOne.class);
		annotations.add(ManyToMany.class);
		annotations.add(ManyToOne.class);
		annotations.add(ElementCollection.class);

		ASSOCIATION_ANNOTATIONS = Collections.unmodifiableSet(annotations);

		annotations = new HashSet<>();
		annotations.add(Id.class);
		annotations.add(EmbeddedId.class);
		annotations.add(Snowflake.class);

		ID_ANNOTATIONS = Collections.unmodifiableSet(annotations);

		annotations = new HashSet<>();
		annotations.add(javax.persistence.Column.class);
		annotations.add(OrderColumn.class);

		UPDATEABLE_ANNOTATIONS = Collections.unmodifiableSet(annotations);

		annotations = new HashSet<>();
		annotations.add(Entity.class);
		annotations.add(MappedSuperclass.class);
		annotations.add(Embeddable.class);

		ENTITY_ANNOTATIONS = Collections.unmodifiableSet(annotations);

	}

	private final @Nullable Boolean usePropertyAccess;

	private final @Nullable TypeInformation<?> associationTargetType;

	private final boolean updateable;

	private final Lazy<Boolean> isIdProperty;

	private final Lazy<Boolean> isAssociation;

	private final Lazy<Boolean> isEntity;

	/**
	 * Creates a new {@link AnnotationBasedPersistentProperty}.
	 * @param property must not be {@literal null}.
	 * @param owner must not be {@literal null}.
	 * @param simpleTypeHolder simple type holder
	 */
	MybatisPersistentPropertyImpl(Property property, PersistentEntity<?, MybatisPersistentProperty> owner,
			SimpleTypeHolder simpleTypeHolder) {
		super(property, owner, simpleTypeHolder);

		this.isAssociation = Lazy.of(() -> ASSOCIATION_ANNOTATIONS.stream().anyMatch(this::isAnnotationPresent));

		this.usePropertyAccess = this.detectPropertyAccess();
		this.associationTargetType = this.detectAssociationTargetType();
		this.updateable = this.detectUpdatability();

		this.isIdProperty = Lazy.of(() -> ID_ANNOTATIONS.stream().anyMatch(this::isAnnotationPresent));
		this.isEntity = Lazy.of(() -> ENTITY_ANNOTATIONS.stream().anyMatch(this::isAnnotationPresent));

	}

	@Override
	public boolean isEnumerated() {
		return this.isAnnotationPresent(Enumerated.class);
	}

	@Override
	public Class<?> getActualType() {
		return (null != this.associationTargetType) ? this.associationTargetType.getType() : super.getActualType();
	}

	@Override
	public Iterable<? extends TypeInformation<?>> getPersistentEntityTypes() {
		return (null != this.associationTargetType) ? Collections.singleton(this.associationTargetType)
				: super.getPersistentEntityTypes();
	}

	@Override
	public boolean isIdProperty() {
		return this.isIdProperty.get();
	}

	@Override
	public boolean isEntity() {
		return this.isEntity.get();
	}

	@Override
	public boolean isAssociation() {
		return this.isAssociation.get();
	}

	@Override
	public boolean isTransient() {
		return this.isAnnotationPresent(Transient.class) || super.isTransient();
	}

	@Override
	protected Association<MybatisPersistentProperty> createAssociation() {
		return new Association<>(this, null);
	}

	@Override
	public boolean usePropertyAccess() {
		return (null != this.usePropertyAccess) ? this.usePropertyAccess : super.usePropertyAccess();
	}

	@Override
	public boolean isVersionProperty() {
		return this.isAnnotationPresent(Version.class) || super.isVersionProperty();
	}

	@Override
	public boolean isWritable() {
		return this.updateable && super.isWritable();
	}

	@Override
	public boolean isEmbeddable() {
		return this.isAnnotationPresent(Embedded.class) || this.hasActualTypeAnnotation(Embeddable.class);
	}

	// @Override
	// public Class<?> getAssociationTargetType() {
	//
	// return
	// ASSOCIATION_ANNOTATIONS.stream().filter(this::isAnnotationPresent).findFirst().map(ann
	// -> {
	// Class<?> t = (Class<?>) ((ann != ElementCollection.class)
	// ? AnnotationUtils.getValue(findAnnotation(ann), "targetEntity")
	// : AnnotationUtils.getValue(findAnnotation(ann), "targetClass"));
	// if (null != t && t != void.class && t != Void.class) {
	// return t;
	// }
	// return null;
	// }).orElseGet(() -> (Class) this.getActualType());
	//
	// }

	/**
	 * Looks up both Spring Data's and JPA's access type definition annotations on the
	 * property or type level to determine the access type to be used. Will consider
	 * property-level annotations over type-level ones, favoring the Spring Data ones over
	 * the JPA ones if found on the same level. Returns {@literal null} if no explicit
	 * annotation can be found falling back to the defaults implemented in the super
	 * class.
	 * @return whether property access
	 */
	@Nullable
	private Boolean detectPropertyAccess() {
		org.springframework.data.annotation.AccessType accessType = this
				.findAnnotation(org.springframework.data.annotation.AccessType.class);
		if (null != accessType) {
			return org.springframework.data.annotation.AccessType.Type.PROPERTY.equals(accessType.value());
		}
		Access access = this.findAnnotation(Access.class);
		if (null != access) {
			return AccessType.PROPERTY.equals(access.value());
		}
		accessType = this.findPropertyOrOwnerAnnotation(org.springframework.data.annotation.AccessType.class);
		if (null != accessType) {
			return org.springframework.data.annotation.AccessType.Type.PROPERTY.equals(accessType.value());
		}
		access = this.findPropertyOrOwnerAnnotation(Access.class);

		if (null != access) {
			return AccessType.PROPERTY.equals(access.value());
		}
		return null;
	}

	/**
	 * Inspects the association annotations on the property and returns the target entity
	 * type if specified.
	 * @return type information
	 */
	@Nullable
	private TypeInformation<?> detectAssociationTargetType() {
		if (!this.isAssociation()) {
			return null;
		}
		for (Class<? extends Annotation> annotationType : ASSOCIATION_ANNOTATIONS) {
			Annotation annotation = this.findAnnotation(annotationType);
			if (null == annotation) {
				continue;
			}
			Object entityValue = AnnotationUtils.getValue(annotation,
					(annotationType != ElementCollection.class) ? "targetEntity" : "targetClass");
			if (null == entityValue || entityValue.equals(void.class)) {
				continue;
			}
			return ClassTypeInformation.from((Class<?>) entityValue);
		}
		return null;
	}

	/**
	 * Checks whether {@code updatable} attribute of any of the
	 * {@link #UPDATEABLE_ANNOTATIONS} is configured to {@literal true}.
	 * @return updatability
	 */
	private boolean detectUpdatability() {
		for (Class<? extends Annotation> annotationType : UPDATEABLE_ANNOTATIONS) {
			Annotation annotation = this.findAnnotation(annotationType);
			if (null == annotation) {
				continue;
			}
			return (boolean) AnnotationUtils.getValue(annotation, "updatable");
		}
		return true;
	}

}
