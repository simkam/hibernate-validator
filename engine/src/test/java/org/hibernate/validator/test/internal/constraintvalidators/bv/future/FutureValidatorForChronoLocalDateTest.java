/*
* JBoss, Home of Professional Open Source
* Copyright 2014, Red Hat, Inc. and/or its affiliates, and individual contributors
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
package org.hibernate.validator.test.internal.constraintvalidators.bv.future;

import java.time.LocalDate;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.HijrahDate;
import java.time.chrono.JapaneseDate;
import java.time.chrono.MinguoDate;
import java.time.chrono.ThaiBuddhistDate;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.hibernate.validator.internal.constraintvalidators.bv.future.FutureValidatorForChronoLocalDate;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link org.hibernate.validator.internal.constraintvalidators.bv.future.FutureValidatorForChronoLocalDate}.
 *
 * @author Khalid Alqinyah
 */
public class FutureValidatorForChronoLocalDateTest {

	private FutureValidatorForChronoLocalDate constraint;

	@BeforeClass
	public void init() {
		constraint = new FutureValidatorForChronoLocalDate();
	}

	@Test
	public void testIsValidForNull() {
		assertTrue( constraint.isValid( null, null ), "null fails validation." );
	}

	@Test
	public void testIsValidForLocalDate() {
		ChronoLocalDate future = LocalDate.now().plusDays( 1 );
		ChronoLocalDate past = LocalDate.now().minusDays( 1 );

		assertTrue( constraint.isValid( future, null ), "Future LocalDate '" + future + "' fails validation.");
		assertFalse( constraint.isValid( past, null ), "Past LocalDate '" + past + "' validated as future.");
	}

	@Test
	public void testIsValidForJapaneseDate() {
		ChronoLocalDate future = JapaneseDate.now().plus( 1, DAYS );
		ChronoLocalDate past = JapaneseDate.now().minus( 1, DAYS );

		assertTrue( constraint.isValid( future, null ), "Future JapaneseDate '" + future + "' fails validation.");
		assertFalse( constraint.isValid( past, null ), "Past JapaneseDate '" + past + "' validated as future.");
	}

	@Test
	public void testIsValidForHijrahDate() {
		ChronoLocalDate future = HijrahDate.now().plus( 1, DAYS );
		ChronoLocalDate past = HijrahDate.now().minus( 1, DAYS );

		assertTrue( constraint.isValid( future, null ), "Future HijrahDate '" + future + "' fails validation.");
		assertFalse( constraint.isValid( past, null ), "Past HijrahDate '" + past + "' validated as future.");
	}

	@Test
	public void testIsValidForMinguoDate() {
		ChronoLocalDate future = MinguoDate.now().plus( 1, DAYS );
		ChronoLocalDate past = MinguoDate.now().minus( 1, DAYS );

		assertTrue( constraint.isValid( future, null ), "Future MinguoDate '" + future + "' fails validation.");
		assertFalse( constraint.isValid( past, null ), "Past MinguoDate '" + past + "' validated as future.");
	}

	@Test
	public void testIsValidForThaiBuddhistDate() {
		ChronoLocalDate future = ThaiBuddhistDate.now().plus( 1, DAYS );
		ChronoLocalDate past = ThaiBuddhistDate.now().minus( 1, DAYS );

		assertTrue( constraint.isValid( future, null ), "Future ThaiBuddhistDate '" + future + "' fails validation.");
		assertFalse( constraint.isValid( past, null ), "Past ThaiBuddhistDate '" + past + "' validated as future.");
	}
}
