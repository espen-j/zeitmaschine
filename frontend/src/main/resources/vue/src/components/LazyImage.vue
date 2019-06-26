<template>
    <figure v-lazyload class="image-lazy-wrapper">
        <img :data-url="'image/thumbnail?name=' + image.name" v-on:click="open(image)">
    </figure>
</template>

<script lang="ts">
    import {Component, Prop, Vue} from "vue-property-decorator";
    import {Image} from "../image/image";
    import axios from 'axios'

    @Component({
        directives: {
            lazyload: function (el) {

                function loadImage() {
                    let imageEl = Array.from(el.children).find(el => el.nodeName === 'IMG');
                    if (imageEl) {
                        axios.request({
                            url: imageEl.dataset.url,
                            responseType: 'blob',
                        })
                            .then(response => response.data)
                            .then(blob => URL.createObjectURL(blob))
                            .then(src => imageEl.src = src )  // OR imageEl.setAttribute("src", src);
                            .catch(e => console.log(e));
                    }
                }

                let callback = (entries, observer) => {
                    entries.forEach((entry) => {
                        if (entry.isIntersecting) {
                            loadImage();
                            observer.unobserve(el);
                        }
                    })
                };
                new IntersectionObserver(callback).observe(el)
            }
        }
    })
    export default class LazyImage extends Vue {

        @Prop()
        protected image!: Image;

    }
</script>