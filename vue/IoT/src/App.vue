<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from './components/common/AppLayout.vue'

const route = useRoute()
const isBlank = computed(() => route.meta.layout === 'blank')
</script>

<template>
  <AppLayout v-if="!isBlank" />
  <router-view v-else />
</template>
