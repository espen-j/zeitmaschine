<template>
    <div class="slider">
        <span class="close-button" v-on:click="close()">X</span>
        <img v-pan="onPan" ref="image" :src="src">
    </div>
</template>

<script lang="ts">
    import {Component, Prop, Vue} from 'vue-property-decorator';
    import 'hammerjs';
    import {Image} from '../image/image';
    import {imageService} from '../image/image-service';

    @Component({
        directives: {
            // https://lisilinhart.info/posts/touch-interaction-vue
            pan: (el, binding) => {
                if (binding.value instanceof Function) {
                    // create a manager for that element
                    let mc = new Hammer.Manager(el);
                    // create a recognizer
                    let pan = new Hammer.Pan({direction: Hammer.DIRECTION_HORIZONTAL});
                    // add the recognizer
                    mc.add(pan);
                    // subscribe to events
                    mc.on('pan', binding.value);
                }
            }
        }
    })
    export default class Slider extends Vue {

        @Prop()
        protected image!: Image;
        private src: string = '';

        protected close() {
            this.$emit('close');
        }

        protected created() {
            return imageService.getImage(this.image.name, 'small')
                .then(blob => URL.createObjectURL(blob))
                .then(src => this.src = src)
                .catch(e => console.log(e));
        }

        onPan(event: any) {
            console.log(event);
            const deltaX = event.deltaX; // moved distance on x-axis
            const deltaY = event.deltaY; // moved distance on y-axis
            const isFinal = event.isFinal; // pan released
            const direction = event.direction; // 0 = none, 2 = left, 4 = right, 8 = up, 16 = down,
            event.target.style.left = event.deltaX + 'px';
            if (isFinal) {
                event.target.style.left = 0 + 'px';
            }
            console.log(deltaX, deltaY, isFinal, direction)
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
            z-index: 100;
        }

        img {
            object-fit: scale-down;
            position: relative;
        }
    }
</style>