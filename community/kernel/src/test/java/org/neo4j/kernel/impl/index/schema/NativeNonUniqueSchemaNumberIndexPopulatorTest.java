/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.index.schema;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.neo4j.index.internal.gbptree.Layout;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.kernel.api.index.IndexEntryUpdate;
import org.neo4j.kernel.api.index.IndexUpdater;
import org.neo4j.kernel.api.schema.index.IndexDescriptor;
import org.neo4j.kernel.impl.api.index.sampling.IndexSamplingConfig;
import org.neo4j.storageengine.api.schema.IndexSample;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.neo4j.helpers.ArrayUtil.array;
import static org.neo4j.index.internal.gbptree.RecoveryCleanupWorkCollector.IMMEDIATE;
import static org.neo4j.kernel.impl.index.schema.FullScanNonUniqueIndexSamplerTest.countUniqueValues;

public class NativeNonUniqueSchemaNumberIndexPopulatorTest
        extends NativeSchemaNumberIndexPopulatorTest<NumberKey,NumberValue>
{
    @Override
    NativeSchemaNumberIndexPopulator<NumberKey,NumberValue> createPopulator( PageCache pageCache, File indexFile,
            Layout<NumberKey,NumberValue> layout, IndexSamplingConfig samplingConfig )
    {
        return new NativeNonUniqueSchemaNumberIndexPopulator<>( pageCache, indexFile, layout, IMMEDIATE, samplingConfig );
    }

    @Test
    public void addShouldApplyDuplicateValues() throws Exception
    {
        // given
        populator.create();
        @SuppressWarnings( "unchecked" )
        IndexEntryUpdate<IndexDescriptor>[] updates = layoutUtil.someUpdatesWithDuplicateValues();

        // when
        populator.add( Arrays.asList( updates ) );

        // then
        populator.close( true );
        verifyUpdates( updates );
    }

    @Test
    public void updaterShouldApplyDuplicateValues() throws Exception
    {
        // given
        populator.create();
        IndexUpdater updater = populator.newPopulatingUpdater( null_property_accessor );
        @SuppressWarnings( "unchecked" )
        IndexEntryUpdate<IndexDescriptor>[] updates = layoutUtil.someUpdatesWithDuplicateValues();

        // when
        for ( IndexEntryUpdate<IndexDescriptor> update : updates )
        {
            updater.process( update );
        }

        // then
        populator.close( true );
        verifyUpdates( updates );
    }

    @Test
    public void shouldFailOnSampleBeforeConfiguredSampling() throws Exception
    {
        // GIVEN
        populator.create();

        // WHEN
        try
        {
            populator.sampleResult();
            fail();
        }
        catch ( IllegalStateException e )
        {
            // THEN good
        }
        populator.close( true );
    }

    @Test
    public void shouldSampleWholeIndexIfConfiguredForPopulatingSampling() throws Exception
    {
        // GIVEN
        populator.create();
        populator.configureSampling( false );
        @SuppressWarnings( "unchecked" )
        IndexEntryUpdate<IndexDescriptor>[] updates = layoutUtil.someUpdates();
        populator.add( Arrays.asList( updates ) );

        // WHEN
        IndexSample sample = populator.sampleResult();

        // THEN
        assertEquals( updates.length, sample.sampleSize() );
        assertEquals( countUniqueValuesAmongUpdates( updates ), sample.uniqueValues() );
        assertEquals( updates.length, sample.indexSize() );
        populator.close( true );
    }

    private long countUniqueValuesAmongUpdates( IndexEntryUpdate<IndexDescriptor>[] updates )
    {
        Set<Double> uniqueValues = new HashSet<>();
        for ( IndexEntryUpdate<IndexDescriptor> update : updates )
        {
            uniqueValues.add( ((Number)update.values()[0]).doubleValue() );
        }
        return uniqueValues.size();
    }

    @Test
    public void shouldSampleUpdatesIfConfiguredForOnlineSampling() throws Exception
    {
        // GIVEN
        populator.create();
        populator.configureSampling( true );
        @SuppressWarnings( "unchecked" )
        IndexEntryUpdate<IndexDescriptor>[] scanUpdates = layoutUtil.someUpdates();
        populator.add( Arrays.asList( scanUpdates ) );
        Number[] updates = array( 101, 102, 102, 103, 103 );
        try ( IndexUpdater updater = populator.newPopulatingUpdater( null_property_accessor ) )
        {
            long nodeId = 1000;
            for ( Number number : updates )
            {
                IndexEntryUpdate<IndexDescriptor> update = layoutUtil.add( nodeId++, number );
                updater.process( update );
                populator.includeSample( update );
            }
        }

        // WHEN
        IndexSample sample = populator.sampleResult();

        // THEN
        assertEquals( updates.length, sample.sampleSize() );
        assertEquals( countUniqueValues( asList( updates ) ), sample.uniqueValues() );
        assertEquals( updates.length, sample.indexSize() );
        populator.close( true );
    }

    @Override
    protected LayoutTestUtil<NumberKey,NumberValue> createLayoutTestUtil()
    {
        return new NonUniqueLayoutTestUtil();
    }
}
