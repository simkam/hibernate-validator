/*
* JBoss, Home of Professional Open Source
* Copyright 2011, Red Hat, Inc. and/or its affiliates, and individual contributors
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.hibernate.validator.internal.metadata.aggregated;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.ConstraintDeclarationException;
import javax.validation.ElementKind;
import javax.validation.metadata.ElementDescriptor;
import javax.validation.metadata.ParameterDescriptor;

import org.hibernate.validator.internal.metadata.core.ConstraintHelper;
import org.hibernate.validator.internal.metadata.core.MetaConstraint;
import org.hibernate.validator.internal.metadata.descriptor.ExecutableDescriptorImpl;
import org.hibernate.validator.internal.metadata.location.ExecutableConstraintLocation;
import org.hibernate.validator.internal.metadata.raw.ConstrainedElement;
import org.hibernate.validator.internal.metadata.raw.ConstrainedElement.ConstrainedElementKind;
import org.hibernate.validator.internal.metadata.raw.ConstrainedExecutable;
import org.hibernate.validator.internal.metadata.raw.ConstrainedParameter;
import org.hibernate.validator.internal.metadata.raw.ExecutableElement;
import org.hibernate.validator.internal.util.logging.Log;
import org.hibernate.validator.internal.util.logging.LoggerFactory;

import static org.hibernate.validator.internal.util.CollectionHelper.newArrayList;
import static org.hibernate.validator.internal.util.CollectionHelper.newHashSet;

/**
 * An aggregated view of the constraint related meta data for a given method or
 * constructors and in (case of methods) all the methods in the inheritance
 * hierarchy which it overrides or implements.
 * <p>
 * Instances are retrieved by creating a {@link Builder} and adding all required
 * {@link ConstrainedExecutable} objects to it. Instances are read-only after
 * creation.
 * </p>
 * <p>
 * Identity is solely based on the method's name and parameter types, hence sets
 * and similar collections of this type may only be created in the scope of one
 * Java type.
 * </p>
 *
 * @author Gunnar Morling
 */
public class ExecutableMetaData extends AbstractConstraintMetaData {

	private static final Log log = LoggerFactory.make();

	private final Class<?>[] parameterTypes;
	private final List<ParameterMetaData> parameterMetaDataList;

	/**
	 * <p>
	 * A declaration exception in case this method violates the rules for
	 * constraints in inheritance hierarchies, outlined in the Bean Validation
	 * specification, section 4.5.5
	 * ("Method constraints in inheritance hierarchies").
	 * </p>
	 * <p>
	 * Such violations shall not hinder standard bean/property validation or
	 * validation of other methods. Therefore this exception is created when
	 * building this meta data object, but it will only be thrown by the
	 * validation engine when actually a method validation is performed or meta
	 * data for this method is requested.
	 * </p>
	 */
	private final ConstraintDeclarationException constraintDeclarationException;

	private final Set<MetaConstraint<?>> crossParameterConstraints;

	/**
	 * An identifier for storing this object in maps etc.
	 */
	private final String identifier;

	private final ReturnValueMetaData returnValueMetaData;

	private ExecutableMetaData(
			String name,
			Class<?> returnType,
			Class<?>[] parameterTypes,
			ElementKind kind,
			Set<MetaConstraint<?>> returnValueConstraints,
			List<ParameterMetaData> parameterMetaData,
			Set<MetaConstraint<?>> crossParameterConstraints,
			ConstraintDeclarationException constraintDeclarationException,
			boolean isCascading,
			boolean isConstrained,
			Map<Class<?>, Class<?>> returnValueGroupConversions) {

		super(
				name,
				returnType,
				returnValueConstraints,
				kind,
				isCascading,
				isConstrained
		);

		this.parameterTypes = parameterTypes;
		this.parameterMetaDataList = Collections.unmodifiableList( parameterMetaData );
		this.crossParameterConstraints = Collections.unmodifiableSet( crossParameterConstraints );
		this.identifier = name + Arrays.toString( parameterTypes );
		this.constraintDeclarationException = constraintDeclarationException;
		this.returnValueMetaData = new ReturnValueMetaData(
				returnType,
				returnValueConstraints,
				isCascading,
				returnValueGroupConversions
		);
	}

	/**
	 * Creates new {@link ExecutableMetaData} instances.
	 *
	 * @author Gunnar Morling
	 * @author Kevin Pollet <kevin.pollet@serli.com> (C) 2011 SERLI
	 */
	public static class Builder extends MetaDataBuilder {

		/**
		 * Either CONSTRUCTOR or METHOD.
		 */
		private final ConstrainedElementKind kind;
		private final Set<ConstrainedExecutable> constrainedExecutables = newHashSet();
		private final ExecutableConstraintLocation location;
		private final Set<MetaConstraint<?>> crossParameterConstraints = newHashSet();
		private boolean isConstrained = false;

		/**
		 * Creates a new builder based on the given executable meta data.
		 *
		 * @param constrainedExecutable The base executable for this builder. This is the lowest
		 * executable with a given signature within a type hierarchy.
		 * @param constraintHelper the constraint helper
		 */
		public Builder(Class<?> beanClass, ConstrainedExecutable constrainedExecutable, ConstraintHelper constraintHelper) {
			super( beanClass, constraintHelper );

			kind = constrainedExecutable.getKind();
			location = constrainedExecutable.getLocation();
			add( constrainedExecutable );
		}

		@Override
		public boolean accepts(ConstrainedElement constrainedElement) {
			if ( kind != constrainedElement.getKind() ) {
				return false;
			}

			ExecutableElement executableElement = ( (ConstrainedExecutable) constrainedElement ).getLocation()
					.getExecutableElement();

			//does one of the executables override the other one?
			return location.getExecutableElement()
					.overrides( executableElement ) || executableElement.overrides(
					location.getExecutableElement()
			);
		}

		@Override
		public void add(ConstrainedElement constrainedElement) {
			super.add( constrainedElement );
			ConstrainedExecutable constrainedExecutable = (ConstrainedExecutable) constrainedElement;

			constrainedExecutables.add( constrainedExecutable );
			isConstrained = isConstrained || constrainedExecutable.isConstrained();
			crossParameterConstraints.addAll( constrainedExecutable.getCrossParameterConstraints() );
		}

		@Override
		public ExecutableMetaData build() {
			ExecutableElement executableElement = location.getExecutableElement();

			ConstraintDeclarationException constraintDeclarationException = checkParameterConstraints();
			if ( constraintDeclarationException == null ) {
				constraintDeclarationException = checkReturnValueConfiguration();
			}

			return new ExecutableMetaData(
					executableElement.getSimpleName(),
					executableElement.getReturnType(),
					executableElement.getParameterTypes(),
					kind == ConstrainedElementKind.CONSTRUCTOR ? ElementKind.CONSTRUCTOR : ElementKind.METHOD,
					adaptOriginsAndImplicitGroups( getConstraints() ),
					findParameterMetaData(),
					adaptOriginsAndImplicitGroups( crossParameterConstraints ),
					constraintDeclarationException,
					isCascading(),
					isConstrained,
					getGroupConversions()
			);
		}

		/**
		 * Finds the one executable from the underlying hierarchy with parameter
		 * constraints. If no executable in the hierarchy is parameter constrained,
		 * the parameter meta data from this builder's base executable is returned.
		 *
		 * @return The parameter meta data for this builder's executable.
		 */
		private List<ParameterMetaData> findParameterMetaData() {
			List<ParameterMetaData.Builder> parameterBuilders = null;

			for ( ConstrainedExecutable oneExecutable : constrainedExecutables ) {

				if ( parameterBuilders == null ) {
					parameterBuilders = newArrayList();

					for ( ConstrainedParameter oneParameter : oneExecutable.getAllParameterMetaData() ) {
						parameterBuilders.add(
								new ParameterMetaData.Builder(
										location.getBeanClass(),
										oneParameter,
										constraintHelper
								)
						);
					}
				}
				else {
					int i = 0;
					for ( ConstrainedParameter oneParameter : oneExecutable.getAllParameterMetaData() ) {
						parameterBuilders.get( i ).add( oneParameter );
						i++;
					}
				}
			}

			List<ParameterMetaData> parameterMetaDatas = newArrayList();

			for ( ParameterMetaData.Builder oneBuilder : parameterBuilders ) {
				parameterMetaDatas.add( oneBuilder.build() );
			}

			return parameterMetaDatas;
		}

		/**
		 * Checks that there are no invalid parameter constraints defined at
		 * this builder's methods.
		 *
		 * @return A {@link ConstraintDeclarationException} describing the first
		 *         illegal method parameter constraint found or {@code null}, if
		 *         the methods of this builder have no such illegal constraints.
		 */
		private ConstraintDeclarationException checkParameterConstraints() {
			Set<ConstrainedExecutable> executablesWithParameterConstraints = executablesWithParameterConstraints(
					constrainedExecutables
			);

			if ( executablesWithParameterConstraints.isEmpty() ) {
				return null;
			}
			Set<Class<?>> definingTypes = newHashSet();

			for ( ConstrainedExecutable constrainedExecutable : executablesWithParameterConstraints ) {
				definingTypes.add( constrainedExecutable.getLocation().getBeanClass() );
			}

			if ( definingTypes.size() > 1 ) {
				return new ConstraintDeclarationException(
						"Only the root method of an overridden method in an inheritance hierarchy may be annotated with parameter constraints, " +
								"but there are parameter constraints defined at all of the following overridden methods: " +
								executablesWithParameterConstraints
				);
			}

			ConstrainedExecutable constrainedExecutable = executablesWithParameterConstraints.iterator()
					.next();

			for ( ConstrainedExecutable oneExecutable : constrainedExecutables ) {

				if ( !constrainedExecutable.getLocation().getBeanClass()
						.isAssignableFrom( oneExecutable.getLocation().getBeanClass() ) ) {
					return new ConstraintDeclarationException(
							"Only the root method of an overridden method in an inheritance hierarchy may be annotated with parameter constraints. " +
									"The following method itself has no parameter constraints but it is not defined on a sub-type of " +
									constrainedExecutable.getLocation()
											.getBeanClass() + ": " + oneExecutable
					);
				}
			}

			return null;
		}

		private ConstraintDeclarationException checkReturnValueConfiguration() {
			ConstrainedExecutable methodWithCascadingReturnValue = null;

			for ( ConstrainedExecutable executable : constrainedExecutables ) {
				if ( executable.getLocation().getExecutableElement().getReturnType() == void.class &&
						( !executable.getConstraints().isEmpty() || executable.isCascading() ) ) {
					return log.voidMethodsMustNotBeConstrained( executable.getLocation().getMember() );
				}

				if ( executable.isCascading() ) {
					if ( methodWithCascadingReturnValue != null ) {
						return log.methodReturnValueMustNotBeMarkedMoreThanOnceForCascadedValidation(
								methodWithCascadingReturnValue.getLocation().getMember(),
								executable.getLocation().getMember()
						);
					}
					methodWithCascadingReturnValue = executable;
				}
			}

			return null;
		}

		/**
		 * Returns a set with those executables from the given pile of executables that have
		 * at least one constrained parameter or at least one parameter annotated
		 * with {@link javax.validation.Valid}.
		 *
		 * @param executables The executables to search in.
		 *
		 * @return A set with constrained executables. May be empty, but never null.
		 */
		private Set<ConstrainedExecutable> executablesWithParameterConstraints(Iterable<ConstrainedExecutable> executables) {
			Set<ConstrainedExecutable> theValue = newHashSet();

			for ( ConstrainedExecutable oneExecutable : executables ) {
				if ( oneExecutable.hasParameterConstraints() ) {
					theValue.add( oneExecutable );
				}
			}

			return theValue;
		}
	}

	/**
	 * <p>
	 * Checks the configuration of this method for correctness as per the rules
	 * outlined in the Bean Validation specification, section 4.5.5
	 * ("Method constraints in inheritance hierarchies").
	 * </p>
	 * <p>
	 * In particular, overriding methods in sub-types may not add parameter
	 * constraints and the return value of an overriding method may not be
	 * marked as cascaded if the return value is marked as cascaded already on
	 * the overridden method.
	 * </p>
	 *
	 * @throws ConstraintDeclarationException In case any of the rules mandated by the specification is
	 * violated.
	 */
	public void assertCorrectnessOfConfiguration()
			throws ConstraintDeclarationException {

		if ( constraintDeclarationException != null ) {
			throw constraintDeclarationException;
		}
	}

	/**
	 * Returns meta data for the specified parameter of the represented executable.
	 *
	 * @param parameterIndex the index of the parameter
	 *
	 * @return Meta data for the specified parameter. Will never be {@code null}.
	 */
	public ParameterMetaData getParameterMetaData(int parameterIndex) {
		if ( parameterIndex < 0 || parameterIndex > parameterMetaDataList.size() - 1 ) {
			throw log.getInvalidMethodParameterIndexException( getName(), parameterIndex );
		}

		return parameterMetaDataList.get( parameterIndex );
	}

	public Class<?>[] getParameterTypes() {
		return parameterTypes;
	}

	/**
	 * Returns an identifier for this meta data object, based on the represented
	 * executable's name and its parameter types.
	 *
	 * @return An identifier for this meta data object.
	 */
	public String getIdentifier() {
		return identifier;
	}

	/**
	 * Returns the cross-parameter constraints declared for the represented
	 * method or constructor.
	 *
	 * @return the cross-parameter constraints declared for the represented
	 *         method or constructor. May be empty but will never be
	 *         {@code null}.
	 */
	public Set<MetaConstraint<?>> getCrossParameterConstraints() {
		return crossParameterConstraints;
	}

	public ValidatableParametersMetaData getValidatableParametersMetaData() {
		Set<ParameterMetaData> cascadedParameters = newHashSet();

		for ( ParameterMetaData parameterMetaData : parameterMetaDataList ) {
			if ( parameterMetaData.isCascading() ) {
				cascadedParameters.add( parameterMetaData );
			}
		}

		return new ValidatableParametersMetaData( cascadedParameters );
	}

	public ReturnValueMetaData getReturnValueMetaData() {
		return returnValueMetaData;
	}

	@Override
	public ExecutableDescriptorImpl asDescriptor(boolean defaultGroupSequenceRedefined, List<Class<?>> defaultGroupSequence) {
		return new ExecutableDescriptorImpl(
				getType(),
				getName(),
				asDescriptors( getCrossParameterConstraints() ),
				returnValueMetaData.asDescriptor(
						defaultGroupSequenceRedefined,
						defaultGroupSequence
				),
				parametersAsDescriptors( defaultGroupSequenceRedefined, defaultGroupSequence ),
				defaultGroupSequenceRedefined,
				defaultGroupSequence,
				constraintDeclarationException
		);
	}

	public ElementDescriptor getDescriptor() {
		return asDescriptor( false, Collections.<Class<?>>emptyList() );
	}

	private List<ParameterDescriptor> parametersAsDescriptors(boolean defaultGroupSequenceRedefined, List<Class<?>> defaultGroupSequence) {
		List<ParameterDescriptor> parameterDescriptorList = newArrayList();

		for ( ParameterMetaData parameterMetaData : parameterMetaDataList ) {
			parameterDescriptorList.add(
					parameterMetaData.asDescriptor(
							defaultGroupSequenceRedefined,
							defaultGroupSequence
					)
			);
		}

		return parameterDescriptorList;
	}

	@Override
	public String toString() {
		StringBuilder parameterBuilder = new StringBuilder();

		for ( Class<?> oneParameterType : getParameterTypes() ) {
			parameterBuilder.append( oneParameterType.getSimpleName() );
			parameterBuilder.append( ", " );
		}

		String parameters =
				parameterBuilder.length() > 0 ?
						parameterBuilder.substring( 0, parameterBuilder.length() - 2 ) :
						parameterBuilder.toString();

		return "ExecutableMetaData [executable=" + getType() + " " + getName() + "(" + parameters + "), isCascading=" + isCascading() + ", isConstrained="
				+ isConstrained() + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Arrays.hashCode( parameterTypes );
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		if ( !super.equals( obj ) ) {
			return false;
		}
		if ( getClass() != obj.getClass() ) {
			return false;
		}
		ExecutableMetaData other = (ExecutableMetaData) obj;
		if ( !Arrays.equals( parameterTypes, other.parameterTypes ) ) {
			return false;
		}
		return true;
	}
}