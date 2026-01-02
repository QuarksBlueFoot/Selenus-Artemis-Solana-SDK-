package com.selenus.artemis.presets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Comprehensive tests for artemis-presets module.
 * Tests ArtemisPreset, PresetRegistry, PresetProvider.
 */
class PresetsModuleTest {

    // ===== ArtemisPreset Tests =====

    @Test
    fun testArtemisPresetImplementation() {
        val preset = object : ArtemisPreset {
            override val id: String = "test.preset.v1"
            override val name: String = "Test Preset"
            override val description: String = "A test preset"
        }
        
        assertEquals("test.preset.v1", preset.id)
        assertEquals("Test Preset", preset.name)
        assertEquals("A test preset", preset.description)
    }

    // ===== PresetRegistry Tests =====

    @Test
    fun testPresetRegistryCreation() {
        val registry = PresetRegistry()
        assertNotNull(registry)
    }

    @Test
    fun testPresetRegistryRegisterAndGet() {
        val registry = PresetRegistry()
        
        val preset = object : ArtemisPreset {
            override val id: String = "test.v1"
            override val name: String = "Test"
            override val description: String = "Test description"
        }
        
        registry.register(preset)
        val retrieved = registry.get("test.v1")
        
        assertNotNull(retrieved)
        assertEquals("Test", retrieved.name)
    }

    @Test
    fun testPresetRegistryGetNonExistent() {
        val registry = PresetRegistry()
        
        val result = registry.get("nonexistent")
        
        assertNull(result)
    }

    @Test
    fun testPresetRegistryRequire() {
        val registry = PresetRegistry()
        
        val preset = object : ArtemisPreset {
            override val id: String = "test.v1"
            override val name: String = "Test"
            override val description: String = "Test description"
        }
        
        registry.register(preset)
        val retrieved = registry.require("test.v1")
        
        assertEquals("Test", retrieved.name)
    }

    @Test
    fun testPresetRegistryRequireNonExistentThrows() {
        val registry = PresetRegistry()
        
        assertFailsWith<PresetRegistry.RegistryError> {
            registry.require("nonexistent")
        }
    }

    @Test
    fun testPresetRegistryAll() {
        val registry = PresetRegistry()
        
        val preset1 = object : ArtemisPreset {
            override val id: String = "test.v1"
            override val name: String = "Test 1"
            override val description: String = "First"
        }
        
        val preset2 = object : ArtemisPreset {
            override val id: String = "test.v2"
            override val name: String = "Test 2"
            override val description: String = "Second"
        }
        
        registry.register(preset1)
        registry.register(preset2)
        
        val all = registry.all()
        
        assertEquals(2, all.size)
    }

    @Test
    fun testPresetRegistryDuplicateThrows() {
        val registry = PresetRegistry()
        
        val preset = object : ArtemisPreset {
            override val id: String = "test.v1"
            override val name: String = "Test"
            override val description: String = "Test description"
        }
        
        registry.register(preset)
        
        assertFailsWith<PresetRegistry.RegistryError> {
            registry.register(preset)
        }
    }

    // ===== PresetProvider Tests =====

    @Test
    fun testPresetProviderFunctionalInterface() {
        val provider = PresetProvider { registry ->
            registry.register(object : ArtemisPreset {
                override val id: String = "provider.v1"
                override val name: String = "Provider Test"
                override val description: String = "From provider"
            })
        }
        
        val registry = PresetRegistry()
        provider.registerInto(registry)
        
        assertNotNull(registry.get("provider.v1"))
    }

    @Test
    fun testPresetRegistryFromProviders() {
        val provider1 = PresetProvider { registry ->
            registry.register(object : ArtemisPreset {
                override val id: String = "p1.v1"
                override val name: String = "Provider 1"
                override val description: String = "First"
            })
        }
        
        val provider2 = PresetProvider { registry ->
            registry.register(object : ArtemisPreset {
                override val id: String = "p2.v1"
                override val name: String = "Provider 2"
                override val description: String = "Second"
            })
        }
        
        val registry = PresetRegistry.fromProviders(listOf(provider1, provider2))
        
        assertEquals(2, registry.all().size)
        assertNotNull(registry.get("p1.v1"))
        assertNotNull(registry.get("p2.v1"))
    }
}
