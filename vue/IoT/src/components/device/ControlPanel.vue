<script setup>
	import {
		ref
	} from 'vue'
	import {
		controlDevice
	} from '../../api/device'
	import {
		VideoPlay,
		VideoPause
	} from '@element-plus/icons-vue'

	const emit = defineEmits(['command-sent'])
	const props = defineProps({
		deviceId: { type: String, default: 'device001' },
	})
	const status = ref('')
	const loading = ref(false)

	// 向ESP8266发送周期采集上报的启停指令。
	async function sendCommand(cmd) {
		const label = cmd === 'start' ? '开始上报' : '停止上报'
		loading.value = true
		status.value = `正在发送 ${label} 指令...`
		try {
			await controlDevice(cmd, props.deviceId)
			status.value = `${label} 指令已受理，等待设备确认`
			emit('command-sent', cmd)
		} catch (e) {
			status.value = `发送失败: ${e.message}`
		} finally {
			loading.value = false
		}
	}
</script>

<template>
	<el-card class="control-panel">
		<template #header>
			<span class="panel-title">设备控制</span>
		</template>
		<div class="control-buttons">
			<el-button type="success" :icon="VideoPlay" :loading="loading" @click="sendCommand('start')">
				开始上报
			</el-button>
			<el-button type="danger" :icon="VideoPause" :loading="loading" @click="sendCommand('stop')">
				停止上报
			</el-button>
		</div>

		<!-- 指令发送状态提示 -->
		<div v-if="status" class="control-status">
			{{ status }}
		</div>
	</el-card>
</template>

<style scoped>
	.control-panel {
		background: rgba(30, 41, 59, 0.5) !important;
		border-color: rgba(255, 255, 255, 0.06) !important;
	}

	.panel-title {
		font-weight: 600;
		font-size: 0.95rem;
	}

	.control-buttons {
		margin-top: 0.625rem;
		display: flex;
		gap: 1rem;
		flex-wrap: wrap;
	}

	.control-status {
		margin-top: 1rem;
		padding: 0.5rem 0.75rem;
		background: rgba(0, 0, 0, 0.2);
		border-radius: 8px;
		font-size: 0.85rem;
		color: var(--text-secondary);
		font-family: 'Courier New', monospace;
	}
</style>
