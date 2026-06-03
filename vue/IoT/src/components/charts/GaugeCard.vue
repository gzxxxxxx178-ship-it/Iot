<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '--' },
  unit: { type: String, default: '' },
  color: { type: String, default: 'var(--color-green)' },
})
</script>

<template>
  <div class="gauge-card" :style="{ '--card-accent': color }">
    <span class="gauge-label">{{ label }}</span>
    <div class="gauge-value">
      <span class="gauge-number">{{ value }}</span>
      <em v-if="unit" class="gauge-unit">{{ unit }}</em>
    </div>
  </div>
</template>

<style scoped>
.gauge-card {
  background: rgba(30, 41, 59, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  padding: 1.2rem;
  backdrop-filter: blur(12px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.2);
  position: relative;
  overflow: hidden;
}

.gauge-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: var(--card-accent);
  opacity: 0.7;
}

.gauge-label {
  color: var(--text-secondary);
  font-size: 0.8rem;
  letter-spacing: 0.6px;
  text-transform: uppercase;
}

.gauge-value {
  margin-top: 0.5rem;
  font-size: 1.8rem;
  font-weight: 600;
  display: flex;
  align-items: baseline;
  gap: 0.3rem;
}

.gauge-number {
  color: var(--text-primary);
}

.gauge-unit {
  font-style: normal;
  color: var(--text-secondary);
  font-size: 0.9rem;
}
</style>
