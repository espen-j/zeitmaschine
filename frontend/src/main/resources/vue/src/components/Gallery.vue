<template>
  <div>
    <div v-for="image in images">
      <img :src="image.thumbnail">
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator';
import {Image} from '../image/image';
import {imageService} from '../image/image-service';

@Component
export default class Gallery extends Vue {

  private images: Image[] = [];

  protected created() {
    console.log('Created');
    imageService.getImages()
            .then(response => this.images = response.data)
            .catch(reason => {
              console.log('Failed', reason);
              this.images = [
                { thumbnail: 'https://www.w3schools.com/howto/img_snow.jpg'},
                { thumbnail: 'https://www.w3schools.com/howto/img_forest.jpg'},
                { thumbnail: 'https://www.w3schools.com/howto/img_mountains.jpg'},
                { thumbnail: 'https://www.w3schools.com/howto/img_snow.jpg'},
                { thumbnail: 'https://www.w3schools.com/howto/img_forest.jpg'},
                { thumbnail: 'https://www.w3schools.com/howto/img_mountains.jpg'},
              ];
            });
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
h3 {
  margin: 40px 0 0;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  display: inline-block;
  margin: 0 10px;
}
a {
  color: #42b983;
}
</style>
