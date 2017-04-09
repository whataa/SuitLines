SuitLines [ ![Download](https://api.bintray.com/packages/yanglssc/maven/suitlines/images/download.svg?version=1.0.0) ](https://bintray.com/yanglssc/maven/suitlines/1.0.0/link)
======================

suitline是一个小巧且高效的线性图表组件。

![image](http://note.youdao.com/yws/api/personal/file/10C1FEB88C5B43B28CCF8164EED2ACD3?method=download&shareKey=585959eab979d7470444426d28ea5cf9)
![image](http://note.youdao.com/yws/api/personal/file/66EB80D81F3A43BAA0B917D4B36275BF?method=download&shareKey=70db0d503ec790dfdab955e4366f8de7)

![image](http://note.youdao.com/yws/api/personal/file/C24FFB56C0F944E9AF4332357F33F09C?method=download&shareKey=156bb9a08b3f4f4d6ae8f5ddac4607fb) ![image](http://note.youdao.com/yws/api/personal/file/CC73337B96A94DA493D6B8E27F9103D7?method=download&shareKey=d0115a84957acf8bba265058460a63d9)

## 功能特性
suitline基于实用性目的而打造，相较于其它图表库，suitlines在多线段、性能体验以及视觉反馈等几个方面进行了支持和优化，使其更适合用于实际项目中。suitLines的所有特性如下：

- 可以为line指定一或多种颜色；

- 支持多条line；

- 支持线段 / 曲线 / 虚线 相互切换；

- 支持边缘拖动反馈效果；

- 支持y轴自定义分隔区间、x轴自定义文本；

- 支持点击反馈；

- 美而不腻的动画；


## 使用步骤

> 注意：SuitLines需要项目的 API >= 14

### 1.集成
- 第一种：通过build.gradle方式集成
    ```groovy
    compile 'tech.linjiang:suitlines:1.0.0'
    ```

- 第二种：直接下载源文件到项目。（**推荐**）

    由于所有的逻辑代码都在`SuitLines.java`中且拥有丰富的注释，所以可以方便地按照实际业务需求来调整或改造。

### 2.在xml布局中调用：
```
<tech.linjiang.suitlines.SuitLines
    xmlns:line="http://schemas.android.com/apk/res-auto"
    android:id="@+id/suitlines"
    android:layout_width="match_parent"
    android:layout_height="200dp"
    line:xySize="8"
    line:xyColor="@color/colorAccent"
    line:lineType="curve"
    line:Style="solid"
    line:needEdgeEffect="true"
    line:colorEdgeEffect="@color/colorPrimaryDark"
    line:needClickHint="true"
    line:colorHint="@color/colorPrimary"
    line:maxOfVisible="7"
    line:countOfY="6"/>
```
所有可静态配置的属性如上，以下是其对应的动态设置方法及其它API：

静态属性 | 对应API | 说明
---|---|---
xySize | setXySize | xy轴文字大小
xyColor | setXyColor | xy轴文字的颜色，包含轴线
lineType | setLineType | 指定line类型：CURVE / SEGMENT（曲线/线段）
Style | setLineStyle | 指定line的风格：DASHED / SOLID（虚线/实线）
needEdgeEffect | disableEdgeEffect | 关闭边缘效果，默认开启
colorEdgeEffect | setEdgeEffectColor | 指定边缘效果的颜色，默认为Color.GRAY
needClickHint | disableClickHint | 关闭点击提示信息，默认开启
colorHint | setHintColor | 设置提示辅助线、文字颜色
maxOfVisible | / | 一组数据在可见区域中的最大可见点数，至少>=2
countOfY | / | y轴刻度数，至少>=1
/ | setLineSize | 设置line在非填充形态时的大小
/ | setLineForm | 设置line的形态：是否填充，默认为false

### 3.填充数据

对于一条line，可以直接调用feed或feedWithAnim方法：
```
List<Unit> lines = new ArrayList<>();
for (int i = 0; i < 14; i++) {
    lines.add(new Unit(new SecureRandom().nextInt(48), i + ""));
}
suitLines.feedWithAnim(lines);
```
如果是多条数据，则需要通过Builder来实现：
```
SuitLines.LineBuilder builder = new SuitLines.LineBuilder();
for (int j = 0; j < count; j++) {
    List<Unit> lines = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
        lines.add(new Unit(new SecureRandom().nextInt(128), "" + i));
    }
    builder.add(lines, new int[]{...});
}
builder.build(suitLines, true);
```




## 说明

感谢star或fork，若需要了解具体实现，请直接clone本工程，源码拥有丰富的注释说明。

有任何Bug或建议欢迎提issue或pull request，或者直接 [反馈给我](y837979117@gmail.com).

## License

Apache 2.0

