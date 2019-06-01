<template>
    <div class="slider">
        <span class="close-button" v-on:click="close()">X</span>
        <img :src="'image/small?name=' + image.name">
    </div>
</template>

<script lang="ts">
    import {Component, Prop, Vue} from 'vue-property-decorator';
    import {Image} from '../image/image';

    @Component
    export default class Slider extends Vue {

        @Prop()
        protected image!: Image;

        protected close() {
            this.$emit('close');
        }
    }
</script>

<style lang="scss">

    .slider {
        position: fixed;
        top: 0;
        bottom: 0;
        left: 0;
        right: 0;
        background-color: white;
        display: flex;
        justify-content: center;
        align-items: center;

        .close-button {
            position: absolute;
            right: 30px;
            top: 30px;
            cursor: pointer;
        }

        img {
            object-fit: scale-down;
        }
    }
</style>